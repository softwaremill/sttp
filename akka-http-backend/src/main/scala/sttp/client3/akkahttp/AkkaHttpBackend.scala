package sttp.client3.akkahttp

import java.io.UnsupportedEncodingException

import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.event.LoggingAdapter
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpEncoding, HttpEncodings}
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, ValidUpgrade, WebSocketRequest}
import akka.http.scaladsl.model.{StatusCode => _, _}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.{ClientTransport, Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client3
import sttp.client3.akkahttp.AkkaHttpBackend.EncodingHandler
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{FollowRedirectsBackend, Response, SttpBackend, SttpBackendOptions, _}
import sttp.model.StatusCode
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.{ExecutionContext, Future, Promise}

class AkkaHttpBackend private (
    actorSystem: ActorSystem,
    ec: ExecutionContext,
    terminateActorSystemOnClose: Boolean,
    opts: SttpBackendOptions,
    customConnectionPoolSettings: Option[ConnectionPoolSettings],
    http: AkkaHttpClient,
    customizeRequest: HttpRequest => HttpRequest,
    customizeWebsocketRequest: WebSocketRequest => WebSocketRequest,
    customEncodingHandler: EncodingHandler
) extends SttpBackend[Future, AkkaStreams with WebSockets] {
  type PE = AkkaStreams with Effect[Future] with WebSockets

  private implicit val as: ActorSystem = actorSystem
  private implicit val _ec: ExecutionContext = ec

  private val connectionPoolSettings = customConnectionPoolSettings
    .getOrElse(ConnectionPoolSettings(actorSystem))
    .withUpdatedConnectionSettings(_.withConnectingTimeout(opts.connectionTimeout))

  override def send[T, R >: PE](r: Request[T, R]): Future[Response[T]] =
    adjustExceptions(r) {
      if (r.isWebSocket) sendWebSocket(r) else sendRegular(r)
    }

  private def sendRegular[T, R >: PE](r: Request[T, R]): Future[Response[T]] = {
    Future
      .fromTry(ToAkka.request(r).flatMap(BodyToAkka(r, r.body, _)))
      .map(customizeRequest)
      .flatMap(request => http.singleRequest(request, connectionSettings(r)))
      .flatMap(responseFromAkka(r, _, None))
  }

  private def sendWebSocket[T, R >: PE](r: Request[T, R]): Future[Response[T]] = {
    val akkaWebsocketRequest = ToAkka
      .headers(r.headers)
      .map(h => WebSocketRequest(uri = r.uri.toString, extraHeaders = h))
      .map(customizeWebsocketRequest)

    val flowPromise = Promise[Flow[Message, Message, NotUsed]]()

    Future
      .fromTry(akkaWebsocketRequest)
      .flatMap(request =>
        http.singleWebsocketRequest(
          request,
          Flow.futureFlow(flowPromise.future),
          connectionSettings(r).connectionSettings
        )
      )
      .flatMap {
        case (ValidUpgrade(response, _), _) =>
          responseFromAkka(r, response, Some(flowPromise))
        case (InvalidUpgradeResponse(response, _), _) =>
          flowPromise.failure(new InterruptedException)
          responseFromAkka(r, response, None)
      }
  }

  override val responseMonad: MonadError[Future] = new FutureMonad()(ec)

  private def connectionSettings(r: Request[_, _]): ConnectionPoolSettings = {
    val connectionPoolSettingsWithProxy = opts.proxy match {
      case Some(p) if !p.ignoreProxy(r.uri.host) =>
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

  private lazy val bodyFromAkka = new BodyFromAkka()(ec, implicitly[Materializer], responseMonad)

  private def responseFromAkka[T](
      r: Request[T, PE],
      hr: HttpResponse,
      wsFlow: Option[Promise[Flow[Message, Message, NotUsed]]]
  ): Future[Response[T]] = {
    val code = StatusCode(hr.status.intValue())
    val statusText = hr.status.reason()

    val headers = FromAkka.headers(hr)

    val responseMetadata = client3.ResponseMetadata(headers, code, statusText)
    val body = bodyFromAkka(r.response, responseMetadata, wsFlow.map(Right(_)).getOrElse(Left(decodeAkkaResponse(hr))))

    body.map(client3.Response(_, code, statusText, headers, Nil, r.onlyMetadata))
  }

  // http://doc.akka.io/docs/akka-http/10.0.7/scala/http/common/de-coding.html
  private def decodeAkkaResponse(response: HttpResponse): HttpResponse = {
    customEncodingHandler.orElse(EncodingHandler(standardEncoding)).apply(response -> response.encoding)
  }

  private def standardEncoding: (HttpResponse, HttpEncoding) => HttpResponse = {
    case (body, HttpEncodings.gzip)     => Gzip.decodeMessage(body)
    case (body, HttpEncodings.deflate)  => Deflate.decodeMessage(body)
    case (body, HttpEncodings.identity) => NoCoding.decodeMessage(body)
    case (_, ce)                        => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  private def adjustExceptions[T](request: Request[_, _])(t: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(FromAkka.exception(request, _))

  override def close(): Future[Unit] = {
    if (terminateActorSystemOnClose) {
      CoordinatedShutdown(as).addTask(
        CoordinatedShutdown.PhaseServiceRequestsDone,
        "shut down all connection pools"
      )(() => Http(as).shutdownAllConnectionPools.map(_ => Done))
      actorSystem.terminate().map(_ => ())
    } else Future.successful(())
  }
}

object AkkaHttpBackend {
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
      http: AkkaHttpClient,
      customizeRequest: HttpRequest => HttpRequest,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Future, AkkaStreams with WebSockets] =
    new FollowRedirectsBackend(
      new AkkaHttpBackend(
        actorSystem,
        ec,
        terminateActorSystemOnClose,
        options,
        customConnectionPoolSettings,
        http,
        customizeRequest,
        customizeWebsocketRequest,
        customEncodingHandler
      )
    )

  /** @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the execution context backing
    *           the given `actorSystem`.
    */
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customHttpsContext: Option[HttpsConnectionContext] = None,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      customLog: Option[LoggingAdapter] = None,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): SttpBackend[Future, AkkaStreams with WebSockets] = {
    val actorSystem = ActorSystem("sttp")

    make(
      actorSystem,
      ec.getOrElse(actorSystem.dispatcher),
      terminateActorSystemOnClose = true,
      options,
      customConnectionPoolSettings,
      AkkaHttpClient.default(actorSystem, customHttpsContext, customLog),
      customizeRequest,
      customizeWebsocketRequest,
      customEncodingHandler
    )
  }

  /** @param actorSystem The actor system which will be used for the http-client
    *                    actors.
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the execution context backing
    *           the given `actorSystem`.
    */
  def usingActorSystem(
      actorSystem: ActorSystem,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customHttpsContext: Option[HttpsConnectionContext] = None,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      customLog: Option[LoggingAdapter] = None,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): SttpBackend[Future, AkkaStreams with WebSockets] = {
    usingClient(
      actorSystem,
      options,
      customConnectionPoolSettings,
      AkkaHttpClient.default(actorSystem, customHttpsContext, customLog),
      customizeRequest,
      customizeWebsocketRequest,
      customEncodingHandler
    )
  }

  /** @param actorSystem The actor system which will be used for the http-client
    *                    actors.
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the execution context backing
    *           the given `actorSystem`.
    */
  def usingClient(
      actorSystem: ActorSystem,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
      http: AkkaHttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: Option[ExecutionContext] = None
  ): SttpBackend[Future, AkkaStreams with WebSockets] = {
    make(
      actorSystem,
      ec.getOrElse(actorSystem.dispatcher),
      terminateActorSystemOnClose = false,
      options,
      customConnectionPoolSettings,
      http,
      customizeRequest,
      customizeWebsocketRequest,
      customEncodingHandler
    )
  }

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackendStub[Future, AkkaStreams with WebSockets] =
    SttpBackendStub(new FutureMonad())
}
