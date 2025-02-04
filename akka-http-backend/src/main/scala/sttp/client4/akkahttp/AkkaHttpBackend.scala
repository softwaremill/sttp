package sttp.client4.akkahttp

import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpEncoding, HttpEncodings}
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, ValidUpgrade, WebSocketRequest}
import akka.http.scaladsl.model.{StatusCode => _, _}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.{ClientTransport, Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4._
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.model.{ResponseMetadata, StatusCode}
import sttp.monad.{FutureMonad, MonadError}
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Decompressor

import scala.concurrent.{ExecutionContext, Future, Promise}

class AkkaHttpBackend private (
    actorSystem: ActorSystem,
    ec: ExecutionContext,
    terminateActorSystemOnClose: Boolean,
    opts: BackendOptions,
    customConnectionPoolSettings: Option[ConnectionPoolSettings],
    http: AkkaHttpClient,
    customizeRequest: HttpRequest => HttpRequest,
    customizeWebsocketRequest: WebSocketRequest => WebSocketRequest,
    customizeResponse: (HttpRequest, HttpResponse) => HttpResponse,
    compressionHandlers: CompressionHandlers[AkkaStreams, HttpResponse]
) extends WebSocketStreamBackend[Future, AkkaStreams] {
  type R = AkkaStreams with WebSockets with Effect[Future]

  private implicit val as: ActorSystem = actorSystem
  private implicit val _ec: ExecutionContext = ec

  private val connectionPoolSettings = customConnectionPoolSettings
    .getOrElse(ConnectionPoolSettings(actorSystem))
    .withUpdatedConnectionSettings(_.withConnectingTimeout(opts.connectionTimeout))

  override def send[T](r: GenericRequest[T, R]): Future[Response[T]] =
    adjustExceptions(r) {
      if (r.isWebSocket) sendWebSocket(r) else sendRegular(r)
    }

  private def sendRegular[T](r: GenericRequest[T, R]): Future[Response[T]] =
    Future
      .fromTry(ToAkka.request(r).flatMap(BodyToAkka(r, _, compressionHandlers.compressors)))
      .map(customizeRequest)
      .flatMap(request =>
        http
          .singleRequest(request, connectionSettings(r))
          .flatMap(response =>
            Future(customizeResponse(request, response))
              .flatMap(response => responseFromAkka(r, response, None).recoverWith(consumeResponseOnFailure(response)))
          )
      )

  private def sendWebSocket[T](r: GenericRequest[T, R]): Future[Response[T]] = {
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
          responseFromAkka(r, response, Some(flowPromise)).recoverWith(consumeResponseOnFailure(response))
        case (InvalidUpgradeResponse(response, _), _) =>
          flowPromise.failure(new InterruptedException)
          responseFromAkka(r, response, None).recoverWith(consumeResponseOnFailure(response))
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

  private lazy val bodyFromAkka = new BodyFromAkka()(ec, implicitly[Materializer], monad)

  private def responseFromAkka[T](
      r: GenericRequest[T, R],
      hr: HttpResponse,
      wsFlow: Option[Promise[Flow[Message, Message, NotUsed]]]
  ): Future[Response[T]] = {
    val code = StatusCode(hr.status.intValue())
    val statusText = hr.status.reason()

    val headers = FromAkka.headers(hr)

    val responseMetadata = ResponseMetadata(code, statusText, headers)
    val body = bodyFromAkka(
      r.response,
      responseMetadata,
      wsFlow
        .map(Right(_))
        .getOrElse(
          Left(
            addOnEndCallback(
              decodeAkkaResponse(
                limitAkkaResponseIfNeeded(hr, r.maxResponseBodyLength),
                r.autoDecompressionEnabled
              ),
              () => r.options.onBodyReceived(responseMetadata)
            )
          )
        )
    )

    body.map(sttp.client4.Response(_, code, statusText, headers, Nil, r.onlyMetadata))
  }

  private def addOnEndCallback(response: HttpResponse, callback: () => Unit): HttpResponse = {
    if (response.entity.isKnownEmpty) {
      callback()
      response
    } else {
      response.transformEntityDataBytes(Flow[ByteString].watchTermination() { case (mat, doneFuture) =>
        doneFuture.onComplete(t => if (t.isSuccess) callback())
        mat
      })
    }
  }

  private def limitAkkaResponseIfNeeded(response: HttpResponse, limit: Option[Long]): HttpResponse =
    limit.fold(response)(l => response.withEntity(response.entity.withSizeLimit(l)))

  // http://doc.akka.io/docs/akka-http/10.0.7/scala/http/common/de-coding.html
  private def decodeAkkaResponse(response: HttpResponse, autoDecompressionEnabled: Boolean): HttpResponse =
    if (!response.status.allowsEntity() || !autoDecompressionEnabled) response
    else
      response.encoding match {
        case HttpEncodings.identity => response
        case encoding: HttpEncoding =>
          Decompressor.decompressIfPossible(response, encoding.value, compressionHandlers.decompressors)
      }

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(monad)(t)(FromAkka.exception(request, _))

  override def close(): Future[Unit] =
    if (terminateActorSystemOnClose) {
      CoordinatedShutdown(as).addTask(
        CoordinatedShutdown.PhaseServiceRequestsDone,
        "shut down all connection pools"
      )(() => Http(as).shutdownAllConnectionPools.map(_ => Done))
      actorSystem.terminate().map(_ => ())
    } else Future.successful(())
}

object AkkaHttpBackend {
  val DefaultCompressionHandlers: CompressionHandlers[AkkaStreams, HttpResponse] =
    CompressionHandlers(
      List(GZipAkkaCompressor, DeflateAkkaCompressor),
      List(GZipAkkaDecompressor, DeflateAkkaDecompressor)
    )

  private def make(
      actorSystem: ActorSystem,
      ec: ExecutionContext,
      terminateActorSystemOnClose: Boolean,
      options: BackendOptions,
      customConnectionPoolSettings: Option[ConnectionPoolSettings],
      http: AkkaHttpClient,
      customizeRequest: HttpRequest => HttpRequest,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      compressionHandlers: CompressionHandlers[AkkaStreams, HttpResponse]
  ): WebSocketStreamBackend[Future, AkkaStreams] =
    FollowRedirectsBackend(
      new AkkaHttpBackend(
        actorSystem,
        ec,
        terminateActorSystemOnClose,
        options,
        customConnectionPoolSettings,
        http,
        customizeRequest,
        customizeWebsocketRequest,
        customizeResponse,
        compressionHandlers
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
      compressionHandlers: CompressionHandlers[AkkaStreams, HttpResponse] = DefaultCompressionHandlers
  )(implicit
      ec: Option[ExecutionContext] = None
  ): WebSocketStreamBackend[Future, AkkaStreams] = {
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
      customizeResponse,
      compressionHandlers
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
      compressionHandlers: CompressionHandlers[AkkaStreams, HttpResponse] = DefaultCompressionHandlers
  )(implicit
      ec: Option[ExecutionContext] = None
  ): WebSocketStreamBackend[Future, AkkaStreams] =
    usingClient(
      actorSystem,
      options,
      customConnectionPoolSettings,
      AkkaHttpClient.default(actorSystem, customHttpsContext, customLog),
      customizeRequest,
      customizeWebsocketRequest,
      customizeResponse,
      compressionHandlers
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
      http: AkkaHttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customizeWebsocketRequest: WebSocketRequest => WebSocketRequest = identity,
      customizeResponse: (HttpRequest, HttpResponse) => HttpResponse = (_, r) => r,
      compressionHandlers: CompressionHandlers[AkkaStreams, HttpResponse] = DefaultCompressionHandlers
  )(implicit
      ec: Option[ExecutionContext] = None
  ): WebSocketStreamBackend[Future, AkkaStreams] =
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
      compressionHandlers
    )

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): WebSocketStreamBackendStub[Future, AkkaStreams with WebSockets] =
    WebSocketStreamBackendStub(new FutureMonad())
}
