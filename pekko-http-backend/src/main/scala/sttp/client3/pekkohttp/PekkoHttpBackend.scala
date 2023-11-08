package sttp.client3.pekkohttp

import java.io.UnsupportedEncodingException
import org.apache.pekko
import pekko.{Done, NotUsed}
import pekko.actor.{ActorSystem, CoordinatedShutdown}
import pekko.event.LoggingAdapter
import pekko.http.scaladsl.coding.Coders
import pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpEncoding, HttpEncodings}
import pekko.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, ValidUpgrade, WebSocketRequest}
import pekko.http.scaladsl.model.{StatusCode => _, _}
import pekko.http.scaladsl.settings.ConnectionPoolSettings
import pekko.http.scaladsl.{ClientTransport, Http, HttpsConnectionContext}
import pekko.stream.Materializer
import pekko.stream.scaladsl.{Flow, Sink}
import sttp.capabilities.pekko.PekkoStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client3
import sttp.client3.pekkohttp.PekkoHttpBackend.EncodingHandler
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{FollowRedirectsBackend, Response, SttpBackend, SttpBackendOptions, _}
import sttp.model.{ResponseMetadata, StatusCode}
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.{ExecutionContext, Future, Promise}

class PekkoHttpBackend private (
    actorSystem: ActorSystem,
    ec: ExecutionContext,
    terminateActorSystemOnClose: Boolean,
    opts: SttpBackendOptions,
    customConnectionPoolSettings: Option[ConnectionPoolSettings],
    http: PekkoHttpClient,
    customizeRequest: HttpRequest => HttpRequest,
    customizeWebsocketRequest: WebSocketRequest => WebSocketRequest,
    customizeResponse: (HttpRequest, HttpResponse) => HttpResponse,
    customEncodingHandler: EncodingHandler
) extends SttpBackend[Future, PekkoStreams with WebSockets] {
  type PE = PekkoStreams with WebSockets with Effect[Future]

  private implicit val as: ActorSystem = actorSystem
  private implicit val _ec: ExecutionContext = ec

  private val connectionPoolSettings = customConnectionPoolSettings
    .getOrElse(ConnectionPoolSettings(actorSystem))
    .withUpdatedConnectionSettings(_.withConnectingTimeout(opts.connectionTimeout))

  override def send[T, R >: PE](r: Request[T, R]): Future[Response[T]] =
    adjustExceptions(r) {
      if (r.isWebSocket) sendWebSocket(r) else sendRegular(r)
    }

  private def sendRegular[T, R >: PE](r: Request[T, R]): Future[Response[T]] =
    Future
      .fromTry(ToPekko.request(r).flatMap(BodyToPekko(r, r.body, _)))
      .map(customizeRequest)
      .flatMap(request =>
        http
          .singleRequest(request, connectionSettings(r))
          .flatMap(response =>
            Future(customizeResponse(request, response))
              .flatMap(response => responseFromPekko(r, response, None).recoverWith(consumeResponseOnFailure(response)))
          )
      )

  private def sendWebSocket[T, R >: PE](r: Request[T, R]): Future[Response[T]] = {
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

  override val responseMonad: MonadError[Future] = new FutureMonad()(ec)

  private def connectionSettings(r: Request[_, _]): ConnectionPoolSettings = {
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

  private lazy val bodyFromPekko = new BodyFromPekko()(ec, implicitly[Materializer], responseMonad)

  private def responseFromPekko[T](
      r: Request[T, PE],
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
      wsFlow.map(Right(_)).getOrElse(Left(decodePekkoResponse(hr, r.autoDecompressionDisabled)))
    )

    body.map(client3.Response(_, code, statusText, headers, Nil, r.onlyMetadata))
  }

  // http://doc.akka.io/docs/akka-http/10.0.7/scala/http/common/de-coding.html
  private def decodePekkoResponse(response: HttpResponse, disableAutoDecompression: Boolean): HttpResponse =
    if (!response.status.allowsEntity() || disableAutoDecompression) response
    else customEncodingHandler.orElse(EncodingHandler(standardEncoding)).apply(response -> response.encoding)

  private def standardEncoding: (HttpResponse, HttpEncoding) => HttpResponse = {
    case (body, HttpEncodings.gzip)     => Coders.Gzip.decodeMessage(body)
    case (body, HttpEncodings.deflate)  => Coders.Deflate.decodeMessage(body)
    case (body, HttpEncodings.identity) => Coders.NoCoding.decodeMessage(body)
    case (_, ce)                        => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  private def adjustExceptions[T](request: Request[_, _])(t: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(FromPekko.exception(request, _))

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
      options: SttpBackendOptions,
      customConnectionPoolSettings: Option[ConnectionPoolSettings],
      http: PekkoHttpClient,
      customizeRequest: HttpRequest => HttpRequest,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Future, PekkoStreams with WebSockets] =
    new FollowRedirectsBackend(
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
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customHttpsContext: Option[HttpsConnectionContext] = None,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      customLog: Option[LoggingAdapter] = None,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): SttpBackend[Future, PekkoStreams with WebSockets] = {
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
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customHttpsContext: Option[HttpsConnectionContext] = None,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      customLog: Option[LoggingAdapter] = None,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): SttpBackend[Future, PekkoStreams with WebSockets] =
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
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      http: PekkoHttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): SttpBackend[Future, PekkoStreams with WebSockets] =
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
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackendStub[Future, PekkoStreams with WebSockets] =
    SttpBackendStub(new FutureMonad())
}
