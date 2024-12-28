package sttp.client4.pekkohttp

import java.io.UnsupportedEncodingException
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.actor.{ActorSystem, CoordinatedShutdown}
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.http.scaladsl.coding.Coders
import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpEncoding, HttpEncodings}
import org.apache.pekko.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, ValidUpgrade, WebSocketRequest}
import org.apache.pekko.http.scaladsl.model.{StatusCode => _, _}
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.http.scaladsl.{ClientTransport, Http, HttpsConnectionContext}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Sink}
import sttp.capabilities.pekko.PekkoStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client4.pekkohttp.PekkoHttpBackend.EncodingHandler
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4._
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.model.{ResponseMetadata, StatusCode}
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.{ExecutionContext, Future, Promise}

class PekkoHttpBackend private (
    actorSystem: ActorSystem,
    ec: ExecutionContext,
    terminateActorSystemOnClose: Boolean,
    opts: BackendOptions,
    customConnectionPoolSettings: Option[ConnectionPoolSettings],
    http: PekkoHttpClient,
    customizeRequest: HttpRequest => HttpRequest,
    customizeWebsocketRequest: WebSocketRequest => WebSocketRequest,
    customizeResponse: (HttpRequest, HttpResponse) => HttpResponse,
    customEncodingHandler: EncodingHandler
) extends WebSocketStreamBackend[Future, PekkoStreams] {
  type R = PekkoStreams with WebSockets with Effect[Future]

  private implicit val as: ActorSystem = actorSystem
  private implicit val _ec: ExecutionContext = ec

  private val connectionPoolSettings = customConnectionPoolSettings
    .getOrElse(ConnectionPoolSettings(actorSystem))
    .withUpdatedConnectionSettings(_.withConnectingTimeout(opts.connectionTimeout))

  override def send[T](r: GenericRequest[T, R]): Future[Response[T]] =
    adjustExceptions(r) {
      if (r.isWebSocket) sendWebSocket(r) else sendRegular(r)
    }

  private val compressors = List(new GZipPekkoCompressor, new DeflatePekkoCompressor)

  private def sendRegular[T](r: GenericRequest[T, R]): Future[Response[T]] =
    Future
      .fromTry(ToPekko.request(r).flatMap(BodyToPekko(r, _, compressors)))
      .map(customizeRequest)
      .flatMap(request =>
        http
          .singleRequest(request, connectionSettings(r))
          .flatMap(response =>
            Future(customizeResponse(request, response))
              .flatMap(response => responseFromPekko(r, response, None).recoverWith(consumeResponseOnFailure(response)))
          )
      )

  private def sendWebSocket[T](r: GenericRequest[T, R]): Future[Response[T]] = {
    val pekkoWebsocketRequest = ToPekko
      .headers(r.headers)
      .map(h => WebSocketRequest(uri = r.uri.toString, extraHeaders = h))
      .map(customizeWebsocketRequest)

    val flowPromise = Promise[Flow[Message, Message, NotUsed]]()

    Future
      .fromTry(pekkoWebsocketRequest)
      .flatMap(request =>
        http.singleWebsocketRequest(
          request,
          Flow.futureFlow(flowPromise.future),
          connectionSettings(r).connectionSettings
        )
      )
      .flatMap {
        case (ValidUpgrade(response, _), _) =>
          responseFromPekko(r, response, Some(flowPromise)).recoverWith(consumeResponseOnFailure(response))
        case (InvalidUpgradeResponse(response, _), _) =>
          flowPromise.failure(new InterruptedException)
          responseFromPekko(r, response, None).recoverWith(consumeResponseOnFailure(response))
      }
  }

  private def consumeResponseOnFailure[T](response: HttpResponse): PartialFunction[Throwable, Future[T]] = {
    case t: Throwable =>
      response.entity.dataBytes
        .runWith(Sink.ignore)
        .flatMap(_ => Future.failed(t))
        .recoverWith { case _ => Future.failed(t) }
  }

  override val monad: MonadError[Future] = new FutureMonad()(ec)

  private def connectionSettings(r: GenericRequest[_, _]): ConnectionPoolSettings = {
    val connectionPoolSettingsWithProxy = opts.proxy match {
      case Some(p) if r.uri.host.forall(!p.ignoreProxy(_)) =>
        val clientTransport = p.auth match {
          case Some(proxyAuth) =>
            ClientTransport.httpsProxy(
              p.inetSocketAddress,
              BasicHttpCredentials(proxyAuth.username, proxyAuth.password)
            )
          case None => ClientTransport.httpsProxy(p.inetSocketAddress)
        }
        connectionPoolSettings.withTransport(clientTransport)
      case _ => connectionPoolSettings
    }
    connectionPoolSettingsWithProxy
      .withUpdatedConnectionSettings(_.withIdleTimeout(r.options.readTimeout))
  }

  private lazy val bodyFromPekko = new BodyFromPekko()(ec, implicitly[Materializer], monad)

  private def responseFromPekko[T](
      r: GenericRequest[T, R],
      hr: HttpResponse,
      wsFlow: Option[Promise[Flow[Message, Message, NotUsed]]]
  ): Future[Response[T]] = {
    val code = StatusCode(hr.status.intValue())
    val statusText = hr.status.reason()

    val headers = FromPekko.headers(hr)

    val responseMetadata = ResponseMetadata(code, statusText, headers)
    val body = bodyFromPekko(
      r.response,
      responseMetadata,
      wsFlow.map(Right(_)).getOrElse(Left(decodePekkoResponse(hr, r.autoDecompressionEnabled)))
    )

    body.map(sttp.client4.Response(_, code, statusText, headers, Nil, r.onlyMetadata))
  }

  // http://doc.akka.io/docs/akka-http/10.0.7/scala/http/common/de-coding.html
  private def decodePekkoResponse(response: HttpResponse, enableAutoDecompression: Boolean): HttpResponse =
    if (!response.status.allowsEntity() || !enableAutoDecompression) response
    else customEncodingHandler.orElse(EncodingHandler(standardEncoding)).apply(response -> response.encoding)

  private def standardEncoding: (HttpResponse, HttpEncoding) => HttpResponse = {
    case (body, HttpEncodings.gzip)     => Coders.Gzip.decodeMessage(body)
    case (body, HttpEncodings.deflate)  => Coders.Deflate.decodeMessage(body)
    case (body, HttpEncodings.identity) => Coders.NoCoding.decodeMessage(body)
    case (_, ce)                        => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(monad)(t)(FromPekko.exception(request, _))

  override def close(): Future[Unit] =
    if (terminateActorSystemOnClose) {
      CoordinatedShutdown(as).addTask(
        CoordinatedShutdown.PhaseServiceRequestsDone,
        "shut down all connection pools"
      )(() => Http(as).shutdownAllConnectionPools().map(_ => Done))
      actorSystem.terminate().map(_ => ())
    } else Future.successful(())
}

object PekkoHttpBackend {
  type EncodingHandler = PartialFunction[(HttpResponse, HttpEncoding), HttpResponse]
  object EncodingHandler {
    def apply(f: (HttpResponse, HttpEncoding) => HttpResponse): EncodingHandler = { case (body, encoding) =>
      f(body, encoding)
    }
  }

  private def make(
      actorSystem: ActorSystem,
      ec: ExecutionContext,
      terminateActorSystemOnClose: Boolean,
      options: BackendOptions,
      customConnectionPoolSettings: Option[ConnectionPoolSettings],
      http: PekkoHttpClient,
      customizeRequest: HttpRequest => HttpRequest,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): WebSocketStreamBackend[Future, PekkoStreams] =
    FollowRedirectsBackend(
      new PekkoHttpBackend(
        actorSystem,
        ec,
        terminateActorSystemOnClose,
        options,
        customConnectionPoolSettings,
        http,
        customizeRequest,
        customizeWebsocketRequest,
        customizeResponse,
        customEncodingHandler
      )
    )

  /** @param ec
    *   The execution context for running non-network related operations, e.g. mapping responses. Defaults to the
    *   execution context backing the given `actorSystem`.
    */
  def apply(
      options: BackendOptions = BackendOptions.Default,
      customHttpsContext: Option[HttpsConnectionContext] = None,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      customLog: Option[LoggingAdapter] = None,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): WebSocketStreamBackend[Future, PekkoStreams] = {
    val actorSystem = ActorSystem("sttp")

    make(
      actorSystem,
      ec.getOrElse(actorSystem.dispatcher),
      terminateActorSystemOnClose = true,
      options,
      customConnectionPoolSettings,
      PekkoHttpClient.default(actorSystem, customHttpsContext, customLog),
      customizeRequest,
      customizeWebsocketRequest,
      customizeResponse,
      customEncodingHandler
    )
  }

  /** @param actorSystem
    *   The actor system which will be used for the http-client actors.
    * @param ec
    *   The execution context for running non-network related operations, e.g. mapping responses. Defaults to the
    *   execution context backing the given `actorSystem`.
    */
  def usingActorSystem(
      actorSystem: ActorSystem,
      options: BackendOptions = BackendOptions.Default,
      customHttpsContext: Option[HttpsConnectionContext] = None,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      customLog: Option[LoggingAdapter] = None,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): WebSocketStreamBackend[Future, PekkoStreams] =
    usingClient(
      actorSystem,
      options,
      customConnectionPoolSettings,
      PekkoHttpClient.default(actorSystem, customHttpsContext, customLog),
      customizeRequest,
      customizeWebsocketRequest,
      customizeResponse,
      customEncodingHandler
    )

  /** @param actorSystem
    *   The actor system which will be used for the http-client actors.
    * @param ec
    *   The execution context for running non-network related operations, e.g. mapping responses. Defaults to the
    *   execution context backing the given `actorSystem`.
    */
  def usingClient(
      actorSystem: ActorSystem,
      options: BackendOptions = BackendOptions.Default,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      http: PekkoHttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): WebSocketStreamBackend[Future, PekkoStreams] =
    make(
      actorSystem,
      ec.getOrElse(actorSystem.dispatcher),
      terminateActorSystemOnClose = false,
      options,
      customConnectionPoolSettings,
      http,
      customizeRequest,
      customizeWebsocketRequest,
      customizeResponse,
      customEncodingHandler
    )

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): WebSocketStreamBackendStub[Future, PekkoStreams with WebSockets] =
    WebSocketStreamBackendStub(new FutureMonad())
}
