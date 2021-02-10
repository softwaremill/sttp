package sttp.client3.armeria

import com.linecorp.armeria.client._
import com.linecorp.armeria.common.MediaType.{OCTET_STREAM, PLAIN_TEXT}
import com.linecorp.armeria.common.{
  AggregatedHttpResponse,
  ClosedSessionException,
  HttpMethod,
  HttpRequest,
  RequestHeaders,
  ResponseHeaders
}
import io.netty.util.AsciiString
import sttp.capabilities
import sttp.client3.SttpClientException.ReadException
import sttp.client3.armeria.ArmeriaBackend.PseudoHeaderPrefix
import sttp.client3.internal.{BodyFromResponseAs, FileHelpers, RichByteBuffer, SttpFile}
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
import java.nio.file.Files
import java.util
import java.util.Map.Entry
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._
import scala.util.Try

class ArmeriaBackend(client: Option[WebClient] = None)(implicit ec: ExecutionContext = ExecutionContext.global)
    extends SttpBackend[Future, Any] {
  type PE = Any with capabilities.Effect[Future]

  override def send[T, R >: PE](request: Request[T, R]): Future[Response[T]] =
    adjustExceptions(request)(execute(request))

  private def execute[T, R >: PE](request: Request[T, R]): Future[Response[T]] =
    getClient(request)
      .execute(requestToArmeria(request))
      .aggregate()
      .asScala
      .flatMap(responseFromArmeria(request, _))

  private def getClient(request: Request[_, _]): WebClient =
    client.getOrElse(
      WebClient
        .builder()
        .responseTimeoutMillis(request.options.readTimeout.toMillis)
        .build()
    )

  private def adjustExceptions[T](request: Request[_, _])(execute: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(responseMonad)(execute) {
      case ex @ (_: ClosedSessionException | _: ResponseTimeoutException) =>
        Some(new ReadException(request, ex))
      case ex =>
        SttpClientException.defaultExceptionToSttpClientException(request, ex)
    }

  private def requestToArmeria(request: Request[_, Nothing]): HttpRequest = {
    val method = methodToArmeria(request.method)
    val path = request.uri.toString()
    val headers = headersToArmeria(request.headers, method, path)

    (request.body match {
      case NoBody                 => HttpRequest.of(method, path)
      case StringBody(s, _, _)    => HttpRequest.of(method, path, PLAIN_TEXT, s)
      case FileBody(f, _)         => HttpRequest.of(method, path, OCTET_STREAM, Files.readAllBytes(f.toPath))
      case ByteArrayBody(b, _)    => HttpRequest.of(method, path, OCTET_STREAM, b)
      case InputStreamBody(is, _) => HttpRequest.of(method, path, OCTET_STREAM, is.readAllBytes())
      case ByteBufferBody(b, _)   => HttpRequest.of(method, path, OCTET_STREAM, b.safeRead())
      case MultipartBody(_)       => throw new IllegalArgumentException("Multipart body is not supported")
      case StreamBody(_)          => throw new IllegalStateException("Streaming is not supported")
    }).withHeaders(headers)
  }

  private def methodToArmeria(method: Method): HttpMethod =
    method match {
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

  private def responseFromArmeria[T, R](
      request: Request[T, R],
      response: AggregatedHttpResponse
  ): Future[Response[T]] = {
    val code = StatusCode.unsafeApply(response.status().code())
    val headers = headersFromArmeria(response.headers())
    val statusText = response.status().reasonPhrase()
    val responseMetadata = ResponseMetadata(code, statusText, headers)
    val encoding = headers.find(_.is(HeaderNames.ContentEncoding)).map(_.value)
    val byteBody = encoding match {
      case Some(enc) if request.method != Method.HEAD => encode(response.content().toInputStream, enc)
      case _                                          => response.content().toInputStream
    }
    val body = bodyFromResponseAs(request.response, responseMetadata, Left(byteBody))
    body.map(Response(_, code, statusText, headers, Nil, request.onlyMetadata))
  }

  private def headersFromArmeria(headers: ResponseHeaders): Seq[Header] = {
    @tailrec
    def accumulate(i: util.Iterator[Entry[AsciiString, String]], acc: Seq[Header] = Nil): Seq[Header] =
      if (i.hasNext) {
        val header = i.next()
        val accNext = if (isPseudoHeader(header)) acc else acc :+ Header(header.getKey.toString, header.getValue)
        accumulate(i, accNext)
      } else acc

    accumulate(headers.iterator())
  }

  private def isPseudoHeader(header: Entry[AsciiString, String]) = header.getKey.startsWith(PseudoHeaderPrefix)

  private def encode: (InputStream, String) => InputStream = {
    case (is, "gzip")    => new GZIPInputStream(is)
    case (is, "deflate") => new InflaterInputStream(is)
    case (_, enc)        => throw new UnsupportedEncodingException(s"Unsupported encoding: $enc")
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

    override protected def regularAsByteArray(response: InputStream): Future[Array[Byte]] =
      Future(response.readAllBytes())

    override protected def regularAsFile(response: InputStream, file: SttpFile): Future[SttpFile] =
      Future(Try(FileHelpers.saveFile(file.toFile, response))).map(_ => file)

    override protected def regularAsStream(response: InputStream): Future[(Nothing, () => Future[Unit])] =
      Future.failed(new IllegalStateException("Streaming is not supported"))

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
  def apply()(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, Any] =
    new FollowRedirectsBackend[Future, Any](new ArmeriaBackend()(ec))

  def usingClient(client: WebClient)(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackend[Future, Any] =
    new FollowRedirectsBackend[Future, Any](new ArmeriaBackend(Some(client))(ec))

  private val PseudoHeaderPrefix = ":"
}
