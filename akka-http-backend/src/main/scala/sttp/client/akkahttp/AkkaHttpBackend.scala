package sttp.client.akkahttp

import java.io.{File, UnsupportedEncodingException}

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.headers.{
  BasicHttpCredentials,
  HttpEncoding,
  HttpEncodings,
  `Content-Length`,
  `Content-Type`
}
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.http.scaladsl.model.{Multipart => AkkaMultipart, StatusCode => _, _}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.{ClientTransport, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Flow, Sink, Source, StreamConverters}
import akka.util.ByteString
import sttp.client
import sttp.client.akkahttp.AkkaHttpBackend.EncodingHandler
import sttp.model.{Header, HeaderNames, Headers, Method, Part, StatusCode}
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocketResponse
import sttp.client.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  FollowRedirectsBackend,
  IgnoreResponse,
  InputStreamBody,
  MappedResponseAs,
  MultipartBody,
  NoBody,
  RequestBody,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsFromMetadata,
  ResponseAsStream,
  ResponseMetadata,
  StreamBody,
  StringBody,
  SttpBackend,
  SttpBackendOptions,
  _
}

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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
) extends SttpBackend[Future, Source[ByteString, Any], Flow[Message, Message, *]] {
  // the supported stream type
  private type S = Source[ByteString, Any]

  private implicit val as: ActorSystem = actorSystem
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val connectionPoolSettings = customConnectionPoolSettings
    .getOrElse(ConnectionPoolSettings(actorSystem))
    .withUpdatedConnectionSettings(_.withConnectingTimeout(opts.connectionTimeout))

  override def send[T](r: Request[T, S]): Future[Response[T]] =
    adjustExceptions {
      implicit val ec: ExecutionContext = this.ec

      Future
        .fromTry(requestToAkka(r).flatMap(setBodyOnAkka(r, r.body, _)))
        .map(customizeRequest)
        .flatMap(request => http.singleRequest(request, connectionSettings(r)))
        .flatMap(responseFromAkka(r, _))
    }

  override def openWebsocket[T, WS_RESULT](
      r: Request[T, Source[ByteString, Any]],
      handler: Flow[Message, Message, WS_RESULT]
  ): Future[WebSocketResponse[WS_RESULT]] =
    adjustExceptions {
      implicit val ec: ExecutionContext = this.ec

      val akkaWebsocketRequest = headersToAkka(r.headers)
        .map(h => WebSocketRequest(uri = r.uri.toString, extraHeaders = h))
        .map(customizeWebsocketRequest)

      Future
        .fromTry(akkaWebsocketRequest)
        .flatMap(request => http.singleWebsocketRequest(request, handler, connectionSettings(r).connectionSettings))
        .flatMap {
          case (wsResponse, wsResult) =>
            responseFromAkka(r, wsResponse.response).map { r =>
              if (r.code != StatusCode.SwitchingProtocols) {
                throw new NotAWebsocketException(r)
              } else {
                client.ws.WebSocketResponse(Headers(r.headers), wsResult)
              }
            }
        }
    }

  override def responseMonad: MonadError[Future] = new FutureMonad()(ec)

  private def methodToAkka(m: Method): HttpMethod =
    m match {
      case Method.GET     => HttpMethods.GET
      case Method.HEAD    => HttpMethods.HEAD
      case Method.POST    => HttpMethods.POST
      case Method.PUT     => HttpMethods.PUT
      case Method.DELETE  => HttpMethods.DELETE
      case Method.OPTIONS => HttpMethods.OPTIONS
      case Method.PATCH   => HttpMethods.PATCH
      case Method.CONNECT => HttpMethods.CONNECT
      case Method.TRACE   => HttpMethods.TRACE
      case _              => HttpMethod.custom(m.method)
    }

  private def bodyFromAkka[T](
      rr: ResponseAs[T, S],
      hr: HttpResponse,
      meta: ResponseMetadata
  ): Future[T] = {
    implicit val ec: ExecutionContext = this.ec

    def asByteArray =
      hr.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .map(_.toArray[Byte])

    def saved(file: File) = {
      if (!file.exists()) {
        file.getParentFile.mkdirs()
        file.createNewFile()
      }

      hr.entity.dataBytes.runWith(FileIO.toPath(file.toPath))
    }

    rr match {
      case MappedResponseAs(raw, g) =>
        bodyFromAkka(raw, hr, meta).map(t => g(t, meta))

      case ResponseAsFromMetadata(f) => bodyFromAkka(f(meta), hr, meta)

      case IgnoreResponse =>
        // todo: Replace with HttpResponse#discardEntityBytes() once https://github.com/akka/akka-http/issues/1459 is resolved
        hr.entity.dataBytes.runWith(Sink.ignore).map(_ => ())

      case ResponseAsByteArray =>
        asByteArray

      case r @ ResponseAsStream() =>
        Future.successful(r.responseIsStream(hr.entity.dataBytes))

      case ResponseAsFile(file) =>
        saved(file.toFile).map(_ => file)
    }
  }

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

  private def responseFromAkka[T](r: Request[T, S], hr: HttpResponse)(implicit
      ec: ExecutionContext
  ): Future[Response[T]] = {
    val code = StatusCode(hr.status.intValue())
    val statusText = hr.status.reason()

    val headers = headersFromAkka(hr)

    val responseMetadata = client.ResponseMetadata(headers, code, statusText)
    val body = bodyFromAkka(r.response, decodeAkkaResponse(hr), responseMetadata)

    body.map(client.Response(_, code, statusText, headers, Nil))
  }

  private def headersFromAkka(hr: HttpResponse): Seq[Header] = {
    val ch = Header(HeaderNames.ContentType, hr.entity.contentType.toString())
    val cl =
      hr.entity.contentLengthOption.map(v => Header.contentLength(v))
    val other = hr.headers.map(h => Header(h.name, h.value))
    ch :: (cl.toList ++ other)
  }

  private def requestToAkka(r: Request[_, S]): Try[HttpRequest] = {
    val ar = HttpRequest(uri = r.uri.toString, method = methodToAkka(r.method))
    headersToAkka(r.headers).map(ar.withHeaders)
  }

  private def headersToAkka(headers: Seq[Header]): Try[Seq[HttpHeader]] = {
    // content-type and content-length headers have to be set via the body
    // entity, not as headers
    val parsed =
      headers
        .filterNot(isContentType)
        .filterNot(isContentLength)
        .map(h => HttpHeader.parse(h.name, h.value))
    val errors = parsed.collect {
      case ParsingResult.Error(e) => e
    }
    if (errors.isEmpty) {
      val headers = parsed.collect {
        case ParsingResult.Ok(h, _) => h
      }

      Success(headers.toList)
    } else {
      Failure(new RuntimeException(s"Cannot parse headers: $errors"))
    }
  }

  private def traverseTry[T](l: Seq[Try[T]]): Try[Seq[T]] = {
    // https://stackoverflow.com/questions/15495678/flatten-scala-try
    val (ss: Seq[Success[T]] @unchecked, fs: Seq[Failure[T]] @unchecked) =
      l.partition(_.isSuccess)

    if (fs.isEmpty) Success(ss.map(_.get))
    else Failure[Seq[T]](fs.head.exception)
  }

  private def setBodyOnAkka(r: Request[_, S], body: RequestBody[S], ar: HttpRequest): Try[HttpRequest] = {
    def ctWithCharset(ct: ContentType, charset: String) =
      HttpCharsets
        .getForKey(charset)
        .map(hc => ContentType.apply(ct.mediaType, () => hc))
        .getOrElse(ct)

    def toBodyPart(mp: Part[BasicRequestBody]): Try[AkkaMultipart.FormData.BodyPart] = {
      def entity(ct: ContentType) =
        mp.body match {
          case StringBody(b, encoding, _) =>
            HttpEntity(ctWithCharset(ct, encoding), b.getBytes(encoding))
          case ByteArrayBody(b, _)  => HttpEntity(ct, b)
          case ByteBufferBody(b, _) => HttpEntity(ct, ByteString(b))
          case isb: InputStreamBody =>
            HttpEntity
              .IndefiniteLength(ct, StreamConverters.fromInputStream(() => isb.b))
          case FileBody(b, _) => HttpEntity.fromPath(ct, b.toPath)
        }

      for {
        ct <- parseContentTypeOrOctetStream(mp.contentType)
        headers <- headersToAkka(mp.headers.toList)
      } yield {
        AkkaMultipart.FormData.BodyPart(mp.name, entity(ct), mp.dispositionParams, headers)
      }
    }

    parseContentTypeOrOctetStream(r).flatMap { ct =>
      body match {
        case NoBody => Success(ar)
        case StringBody(b, encoding, _) =>
          Success(ar.withEntity(ctWithCharset(ct, encoding), b.getBytes(encoding)))
        case ByteArrayBody(b, _) => Success(ar.withEntity(HttpEntity(ct, b)))
        case ByteBufferBody(b, _) =>
          Success(ar.withEntity(HttpEntity(ct, ByteString(b))))
        case InputStreamBody(b, _) =>
          Success(ar.withEntity(HttpEntity(ct, StreamConverters.fromInputStream(() => b))))
        case FileBody(b, _) => Success(ar.withEntity(ct, b.toPath))
        case StreamBody(s)  => Success(ar.withEntity(HttpEntity(ct, s)))
        case MultipartBody(ps) =>
          traverseTry(ps.map(toBodyPart))
            .map(bodyParts => ar.withEntity(AkkaMultipart.FormData(bodyParts: _*).toEntity()))
      }
    }
  }

  private def parseContentTypeOrOctetStream(r: Request[_, S]): Try[ContentType] = {
    parseContentTypeOrOctetStream(
      r.headers
        .find(isContentType)
        .map(_.value)
    )
  }

  private def parseContentTypeOrOctetStream(ctHeader: Option[String]): Try[ContentType] = {
    ctHeader
      .map { ct =>
        ContentType
          .parse(ct)
          .fold(errors => Failure(new RuntimeException(s"Cannot parse content type: $errors")), Success(_))
      }
      .getOrElse(Success(`application/octet-stream`))
  }

  private def isContentType(header: Header) =
    header.name.toLowerCase.contains(`Content-Type`.lowercaseName)

  private def isContentLength(header: Header) =
    header.name.toLowerCase.contains(`Content-Length`.lowercaseName)

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

  private def adjustExceptions[T](t: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(akkaExceptionToSttpClientException)

  private def akkaExceptionToSttpClientException(e: Exception): Option[Exception] =
    e match {
      case e: akka.stream.ConnectionException => Some(new SttpClientException.ConnectException(e))
      case e: akka.stream.StreamTcpException =>
        e.getCause match {
          case ee: Exception =>
            akkaExceptionToSttpClientException(ee).orElse(Some(new SttpClientException.ReadException(e)))
          case _ => Some(new SttpClientException.ReadException(e))
        }
      case e: akka.stream.scaladsl.TcpIdleTimeoutException => Some(new SttpClientException.ReadException(e))
      case e: Exception                                    => SttpClientException.defaultExceptionToSttpClientException(e)
    }

  override def close(): Future[Unit] = {
    import as.dispatcher
    if (terminateActorSystemOnClose) actorSystem.terminate().map(_ => ()) else Future.successful(())
  }
}

object AkkaHttpBackend {
  type EncodingHandler = PartialFunction[(HttpResponse, HttpEncoding), HttpResponse]
  object EncodingHandler {
    def apply(f: (HttpResponse, HttpEncoding) => HttpResponse): EncodingHandler = {
      case (body, encoding) => f(body, encoding)
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
  ): SttpBackend[Future, Source[ByteString, Any], Flow[Message, Message, *]] =
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

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
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
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackend[Future, Source[ByteString, Any], Flow[Message, Message, *]] = {
    val actorSystem = ActorSystem("sttp")

    make(
      actorSystem,
      ec,
      terminateActorSystemOnClose = true,
      options,
      customConnectionPoolSettings,
      AkkaHttpClient.default(actorSystem, customHttpsContext, customLog),
      customizeRequest,
      customizeWebsocketRequest,
      customEncodingHandler
    )
  }

  /**
    * @param actorSystem The actor system which will be used for the http-client
    *                    actors.
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
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
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackend[Future, Source[ByteString, Any], Flow[Message, Message, *]] = {
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

  /**
    * @param actorSystem The actor system which will be used for the http-client
    *                    actors.
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
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
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackend[Future, Source[ByteString, Any], Flow[Message, Message, *]] = {
    make(
      actorSystem,
      ec,
      terminateActorSystemOnClose = false,
      options,
      customConnectionPoolSettings,
      http,
      customizeRequest,
      customizeWebsocketRequest,
      customEncodingHandler
    )
  }

  /**
    * Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackendStub[Future, Nothing] =
    SttpBackendStub(new FutureMonad())
}

class NotAWebsocketException(r: Response[_]) extends Exception
