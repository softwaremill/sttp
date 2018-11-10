package com.softwaremill.sttp.akkahttp

import java.io.{File, IOException, UnsupportedEncodingException}

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.headers.{HttpEncodings, `Content-Length`, `Content-Type`}
import akka.http.scaladsl.model.{Multipart => AkkaMultipart, _}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.{ClientTransport, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source, StreamConverters}
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.internal._

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class AkkaHttpBackend private (actorSystem: ActorSystem,
                               ec: ExecutionContext,
                               terminateActorSystemOnClose: Boolean,
                               opts: SttpBackendOptions,
                               customHttpsContext: Option[HttpsConnectionContext],
                               customConnectionPoolSettings: Option[ConnectionPoolSettings],
                               customLog: Option[LoggingAdapter])
    extends SttpBackend[Future, Source[ByteString, Any]] {

  // the supported stream type
  private type S = Source[ByteString, Any]

  private implicit val as: ActorSystem = actorSystem
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val http = Http()

  private val connectionPoolSettings = customConnectionPoolSettings
    .getOrElse(ConnectionPoolSettings(actorSystem))
    .withUpdatedConnectionSettings(_.withConnectingTimeout(opts.connectionTimeout))

  override def send[T](r: Request[T, S]): Future[Response[T]] = {
    implicit val ec: ExecutionContext = this.ec

    val connectionPoolSettingsWithProxy = opts.proxy match {
      case Some(p) if !p.ignoreProxy(r.uri.host) =>
        connectionPoolSettings.withTransport(ClientTransport.httpsProxy(p.inetSocketAddress))
      case _ => connectionPoolSettings
    }
    val settings = connectionPoolSettingsWithProxy
      .withUpdatedConnectionSettings(_.withIdleTimeout(r.options.readTimeout))

    requestToAkka(r)
      .flatMap(setBodyOnAkka(r, r.body, _))
      .toFuture
      .flatMap(
        req =>
          http.singleRequest(req,
                             settings = settings,
                             connectionContext = customHttpsContext.getOrElse(http.defaultClientHttpsContext),
                             log = customLog.getOrElse(actorSystem.log)))
      .flatMap { hr =>
        val code = hr.status.intValue()
        val statusText = hr.status.reason()

        val headers = headersFromAkka(hr)
        val charsetFromHeaders = headers
          .find(_._1 == HeaderNames.ContentType)
          .map(_._2)
          .flatMap(encodingFromContentType)

        val responseMetadata = ResponseMetadata(headers, code, statusText)
        val body = if (r.options.parseResponseIf(code)) {
          bodyFromAkka(r.response, decodeAkkaResponse(hr), charsetFromHeaders, responseMetadata)
            .map(Right(_))
        } else {
          bodyFromAkka(asByteArray, decodeAkkaResponse(hr), charsetFromHeaders, responseMetadata)
            .map(Left(_))
        }

        body.map(Response(_, code, statusText, headers, Nil))
      }
  }

  override def responseMonad: MonadError[Future] = new FutureMonad()(ec)

  private def methodToAkka(m: Method): HttpMethod = m match {
    case Method.GET     => HttpMethods.GET
    case Method.HEAD    => HttpMethods.HEAD
    case Method.POST    => HttpMethods.POST
    case Method.PUT     => HttpMethods.PUT
    case Method.DELETE  => HttpMethods.DELETE
    case Method.OPTIONS => HttpMethods.OPTIONS
    case Method.PATCH   => HttpMethods.PATCH
    case Method.CONNECT => HttpMethods.CONNECT
    case Method.TRACE   => HttpMethods.TRACE
    case _              => HttpMethod.custom(m.m)
  }

  private def bodyFromAkka[T](rr: ResponseAs[T, S],
                              hr: HttpResponse,
                              charsetFromHeaders: Option[String],
                              headers: ResponseMetadata): Future[T] = {

    implicit val ec: ExecutionContext = this.ec

    def asByteArray =
      hr.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .map(_.toArray[Byte])

    def saved(file: File, overwrite: Boolean) = {
      if (!file.exists()) {
        file.getParentFile.mkdirs()
        file.createNewFile()
      } else if (!overwrite) {
        throw new IOException(s"File ${file.getAbsolutePath} exists - overwriting prohibited")
      }

      hr.entity.dataBytes.runWith(FileIO.toPath(file.toPath))
    }

    rr match {
      case MappedResponseAs(raw, g) =>
        bodyFromAkka(raw, hr, charsetFromHeaders, headers).map(t => g(t, headers))

      case IgnoreResponse =>
        // todo: Replace with HttpResponse#discardEntityBytes() once https://github.com/akka/akka-http/issues/1459 is resolved
        hr.entity.dataBytes.runWith(Sink.ignore)
        Future.successful(())

      case ResponseAsString(enc) =>
        asByteArray.map(new String(_, charsetFromHeaders.getOrElse(enc)))

      case ResponseAsByteArray =>
        asByteArray

      case r @ ResponseAsStream() =>
        Future.successful(r.responseIsStream(hr.entity.dataBytes))

      case ResponseAsFile(file, overwrite) =>
        saved(file.toFile, overwrite).map(_ => file)
    }
  }

  private def headersFromAkka(hr: HttpResponse): Seq[(String, String)] = {
    val ch = HeaderNames.ContentType -> hr.entity.contentType.toString()
    val cl =
      hr.entity.contentLengthOption.map(HeaderNames.ContentLength -> _.toString)
    val other = hr.headers.map(h => (h.name, h.value))
    ch :: (cl.toList ++ other)
  }

  private def requestToAkka(r: Request[_, S]): Try[HttpRequest] = {
    val ar = HttpRequest(uri = r.uri.toString, method = methodToAkka(r.method))
    headersToAkka(r.headers).map(ar.withHeaders)
  }

  private def headersToAkka(headers: Seq[(String, String)]): Try[Seq[HttpHeader]] = {
    // content-type and content-length headers have to be set via the body
    // entity, not as headers
    val parsed =
      headers
        .filterNot(isContentType)
        .filterNot(isContentLength)
        .map(h => HttpHeader.parse(h._1, h._2))
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
    def ctWithEncoding(ct: ContentType, encoding: String) =
      HttpCharsets
        .getForKey(encoding)
        .map(hc => ContentType.apply(ct.mediaType, () => hc))
        .getOrElse(ct)

    def toBodyPart(mp: Multipart): Try[AkkaMultipart.FormData.BodyPart] = {
      def entity(ct: ContentType) = mp.body match {
        case StringBody(b, encoding, _) =>
          HttpEntity(ctWithEncoding(ct, encoding), b.getBytes(encoding))
        case ByteArrayBody(b, _)  => HttpEntity(ct, b)
        case ByteBufferBody(b, _) => HttpEntity(ct, ByteString(b))
        case isb: InputStreamBody =>
          HttpEntity
            .IndefiniteLength(ct, StreamConverters.fromInputStream(() => isb.b))
        case FileBody(b, _) => HttpEntity.fromPath(ct, b.toPath)
      }

      for {
        ct <- parseContentTypeOrOctetStream(mp.contentType)
        headers <- headersToAkka(mp.additionalHeaders.toList)
      } yield {
        val dispositionParams =
          mp.fileName.fold(Map.empty[String, String])(fn => Map("filename" -> fn))

        AkkaMultipart.FormData.BodyPart(mp.name, entity(ct), dispositionParams, headers)
      }
    }

    parseContentTypeOrOctetStream(r).flatMap { ct =>
      body match {
        case NoBody => Success(ar)
        case StringBody(b, encoding, _) =>
          Success(ar.withEntity(ctWithEncoding(ct, encoding), b.getBytes(encoding)))
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
        .map(_._2))
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

  private def isContentType(header: (String, String)) =
    header._1.toLowerCase.contains(`Content-Type`.lowercaseName)

  private def isContentLength(header: (String, String)) =
    header._1.toLowerCase.contains(`Content-Length`.lowercaseName)

  // http://doc.akka.io/docs/akka-http/10.0.7/scala/http/common/de-coding.html
  private def decodeAkkaResponse(response: HttpResponse): HttpResponse = {
    val decoder = response.encoding match {
      case HttpEncodings.gzip     => Gzip
      case HttpEncodings.deflate  => Deflate
      case HttpEncodings.identity => NoCoding
      case ce =>
        throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
    }

    decoder.decodeMessage(response)
  }

  override def close(): Unit = {
    if (terminateActorSystemOnClose) actorSystem.terminate()
  }

  private implicit class RichTry[T](t: Try[T]) {
    def toFuture: Future[T] = t match {
      case Success(v) => Future.successful(v)
      case Failure(v) => Future.failed(v)
    }
  }
}

object AkkaHttpBackend {
  private def apply(actorSystem: ActorSystem,
                    ec: ExecutionContext,
                    terminateActorSystemOnClose: Boolean,
                    options: SttpBackendOptions,
                    customHttpsContext: Option[HttpsConnectionContext],
                    customConnectionPoolSettings: Option[ConnectionPoolSettings],
                    customLog: Option[LoggingAdapter]): SttpBackend[Future, Source[ByteString, Any]] =
    new FollowRedirectsBackend(
      new AkkaHttpBackend(actorSystem,
                          ec,
                          terminateActorSystemOnClose,
                          options,
                          customHttpsContext,
                          customConnectionPoolSettings,
                          customLog))

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply(options: SttpBackendOptions = SttpBackendOptions.Default,
            customHttpsContext: Option[HttpsConnectionContext] = None,
            customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
            customLog: Option[LoggingAdapter] = None)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Source[ByteString, Any]] =
    AkkaHttpBackend(ActorSystem("sttp"),
                    ec,
                    terminateActorSystemOnClose = true,
                    options,
                    customHttpsContext,
                    customConnectionPoolSettings,
                    customLog)

  /**
    * @param actorSystem The actor system which will be used for the http-client
    *                    actors.
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingActorSystem(actorSystem: ActorSystem,
                       options: SttpBackendOptions = SttpBackendOptions.Default,
                       customHttpsContext: Option[HttpsConnectionContext] = None,
                       customConnectionPoolSettings: Option[ConnectionPoolSettings] = None,
                       customLog: Option[LoggingAdapter] = None)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Source[ByteString, Any]] =
    AkkaHttpBackend(actorSystem,
                    ec,
                    terminateActorSystemOnClose = false,
                    options,
                    customHttpsContext,
                    customConnectionPoolSettings,
                    customLog)
}
