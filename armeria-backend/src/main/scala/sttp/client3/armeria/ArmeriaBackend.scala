package sttp.client3.armeria

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.MediaType.{OCTET_STREAM, PLAIN_TEXT}
import com.linecorp.armeria.common.{HttpMethod, HttpRequest, RequestHeaders, ResponseHeaders}
import io.netty.util.AsciiString
import sttp.capabilities
import sttp.client3.armeria.ArmeriaBackend.{
  MultipartNotSupportedException,
  PseudoHeaderPrefix,
  StreamingNotSupportedException
}
import sttp.client3.internal.{BodyFromResponseAs, FileHelpers, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  FollowRedirectsBackend,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  Response,
  StreamBody,
  StringBody,
  SttpBackend,
  SttpClientException,
  WebSocketResponseAs
}
import sttp.model._
import sttp.monad.{FutureMonad, MonadError}

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.util
import java.util.Map.Entry
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.jdk.FutureConverters._
import scala.util.Try

class ArmeriaBackend(implicit ec: ExecutionContext) extends SttpBackend[Future, Any] {
  type PE = Any with capabilities.Effect[Future]

  override def send[T, R >: PE](request: Request[T, R]): Future[Response[T]] =
    adjustExceptions(request)(execute(request))

  private def execute[T, R >: PE](request: Request[T, R]): Future[Response[T]] = {
    val aRequest = requestBodyToArmeria(request)
    WebClient
      .of()
      .execute(aRequest)
      .aggregate()
      .asScala
      .flatMap { aResponse =>
        val code = StatusCode.unsafeApply(aResponse.status().code())
        val headers = headersFromArmeria(aResponse.headers())
        val statusText = aResponse.status().reasonPhrase()
        val responseMetadata = ResponseMetadata(code, statusText, headers)
        val encoding = headers.find(_.is(HeaderNames.ContentEncoding)).map(_.value)
        val byteBody = encoding match {
          case Some(enc) if request.method != Method.HEAD => encode(aResponse.content().toInputStream, enc)
          case _                                          => aResponse.content().toInputStream
        }
        val body = bodyFromResponseAs(request.response, responseMetadata, Left(byteBody))
        body.map(Response(_, code, statusText, headers, Nil, request.onlyMetadata))
      }(ec)
  }

  private def adjustExceptions[T](request: Request[_, _])(t: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  private def requestBodyToArmeria(request: Request[_, Nothing]): HttpRequest = {
    val method = methodToArmeria(request.method)
    val path = request.uri.toString()
    val headers = headersToArmeria(request.headers, method, path)

    (request.body match {
      case NoBody                 => HttpRequest.of(method, path)
      case StringBody(s, _, _)    => HttpRequest.of(method, path, PLAIN_TEXT, s)
      case FileBody(f, _)         => HttpRequest.of(method, path, OCTET_STREAM, Source.fromFile(f.toFile).mkString)
      case ByteArrayBody(b, _)    => HttpRequest.of(method, path, OCTET_STREAM, Source.fromBytes(b).mkString)
      case InputStreamBody(is, _) => HttpRequest.of(method, path, OCTET_STREAM, Source.fromInputStream(is).mkString)
      case ByteBufferBody(b, _)   => HttpRequest.of(method, path, OCTET_STREAM, UTF_8.decode(b).toString)
      case MultipartBody(_)       => throw MultipartNotSupportedException
      case StreamBody(_)          => throw StreamingNotSupportedException
    }).withHeaders(headers)
  }

  private def methodToArmeria(m: Method): HttpMethod =
    m match {
      case Method.GET     => HttpMethod.GET
      case Method.HEAD    => HttpMethod.HEAD
      case Method.POST    => HttpMethod.POST
      case Method.PUT     => HttpMethod.PUT
      case Method.DELETE  => HttpMethod.DELETE
      case Method.OPTIONS => HttpMethod.OPTIONS
      case Method.PATCH   => HttpMethod.PATCH
      case Method.CONNECT => HttpMethod.CONNECT
      case Method.TRACE   => HttpMethod.TRACE
      case _              => HttpMethod.UNKNOWN
    }

  private def headersToArmeria(headers: Seq[Header], method: HttpMethod, path: String): RequestHeaders = {
    val builder = RequestHeaders.builder(method, path)
    headers.foreach(h => builder.add(h.name, h.value))
    builder.build()
  }

  private def headersFromArmeria(headers: ResponseHeaders): Seq[Header] = {
    @tailrec
    def accumulateHeaders(i: util.Iterator[Entry[AsciiString, String]], acc: Seq[Header] = Nil): Seq[Header] =
      if (i.hasNext) {
        val header = i.next()
        val accNext = if (isPseudoHeader(header)) acc else acc :+ Header(header.getKey.toString, header.getValue)
        accumulateHeaders(i, accNext)
      } else acc

    accumulateHeaders(headers.iterator())
  }

  private def isPseudoHeader(header: Entry[AsciiString, String]) = header.getKey.startsWith(PseudoHeaderPrefix)

  private def encode: (InputStream, String) => InputStream = {
    case (b, "gzip")    => new GZIPInputStream(b)
    case (b, "deflate") => new InflaterInputStream(b)
    case (_, enc)       => throw new UnsupportedEncodingException(s"Unsupported encoding: $enc")
  }

  private lazy val bodyFromResponseAs = new BodyFromResponseAs[Future, InputStream, Nothing, Nothing] {
    override protected def withReplayableBody(
        response: InputStream,
        replayableBody: Either[Array[Byte], SttpFile]
    ): Future[InputStream] = Future(replayableBody match {
      case Left(bytes) => new ByteArrayInputStream(bytes)
      case Right(file) => new BufferedInputStream(new FileInputStream(file.toFile))
    })

    override protected def regularIgnore(response: InputStream): Future[Unit] = Future.unit

    override protected def regularAsByteArray(response: InputStream): Future[Array[Byte]] = Future(
      response.readAllBytes()
    )

    override protected def regularAsFile(response: InputStream, file: SttpFile): Future[SttpFile] =
      Future(Try(FileHelpers.saveFile(file.toFile, response))).map(_ => file)

    override protected def regularAsStream(response: InputStream): Future[(Nothing, () => Future[Unit])] =
      Future.failed(StreamingNotSupportedException)

    override protected def handleWS[T](
        responseAs: WebSocketResponseAs[T, _],
        meta: ResponseMetadata,
        ws: Nothing
    ): Future[T] = ws

    override protected def cleanupWhenNotAWebSocket(response: InputStream, e: NotAWebSocketException): Future[Unit] =
      Future(response.close())

    override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): Future[Unit] =
      response
  }

  override def close(): Future[Unit] = Future.unit

  override implicit def responseMonad: MonadError[Future] = new FutureMonad()(ec)
}

object ArmeriaBackend {
  def apply()(implicit ec: ExecutionContext): SttpBackend[Future, Any] =
    new FollowRedirectsBackend[Future, Any](new ArmeriaBackend()(ec))

  private val PseudoHeaderPrefix = ":"
  private val MultipartNotSupportedException = new IllegalArgumentException("Multipart body is not supported")
  private val StreamingNotSupportedException = new IllegalStateException("Streaming is not supported")
}
