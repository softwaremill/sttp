package sttp.client4.httpurlconnection

import sttp.capabilities.Effect
import sttp.client4.httpurlconnection.HttpURLConnectionBackend.EncodingHandler
import sttp.client4.internal._
import sttp.client4.testing.SyncBackendStub
import sttp.client4.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client4.{
  wrappers,
  BackendOptions,
  BasicBodyPart,
  BasicMultipartBody,
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  GenericRequest,
  GenericWebSocketResponseAs,
  InputStreamBody,
  MultipartStreamBody,
  NoBody,
  Response,
  StreamBody,
  StringBody,
  SttpClientException,
  SyncBackend
}
import sttp.model._
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

import java.io._
import java.net._
import java.nio.channels.Channels
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

class HttpURLConnectionBackend private (
    opts: BackendOptions,
    customizeConnection: HttpURLConnection => Unit,
    createURL: String => URL,
    openConnection: (URL, Option[java.net.Proxy]) => URLConnection,
    customEncodingHandler: EncodingHandler
) extends SyncBackend {
  type R = Any with Effect[Identity]

  override def send[T](r: GenericRequest[T, R]): Response[T] =
    adjustExceptions(r) {
      val c = openConnection(r.uri)
      c.setRequestMethod(r.method.method)
      r.headers.foreach(h => c.setRequestProperty(h.name, h.value))
      c.setDoInput(true)
      c.setReadTimeout(timeout(r.options.readTimeout))
      c.setConnectTimeout(timeout(opts.connectionTimeout))

      // redirects are handled by FollowRedirectsBackend
      c.setInstanceFollowRedirects(false)

      customizeConnection(c)

      if (r.body != NoBody) {
        c.setDoOutput(true)
        // we need to take care to:
        // (1) only call getOutputStream after the headers are set
        // (2) call it ony once
        writeBody(r, c).foreach { os =>
          os.flush()
          os.close()
        }
      }

      try {
        val is = c.getInputStream
        readResponse(c, is, r)
      } catch {
        case e: CharacterCodingException     => throw e
        case e: UnsupportedEncodingException => throw e
        case e: SocketException              => throw e
        case _: IOException if c.getResponseCode != -1 =>
          readResponse(c, c.getErrorStream, r)
      }
    }

  override implicit val monad: MonadError[Identity] = IdentityMonad

  private def openConnection(uri: Uri): HttpURLConnection = {
    val url = createURL(uri.toString)
    val conn = opts.proxy match {
      case Some(p) if uri.host.forall(!p.ignoreProxy(_)) =>
        p.auth.foreach { proxyAuth =>
          Authenticator.setDefault(new Authenticator() {
            override def getPasswordAuthentication: PasswordAuthentication =
              new PasswordAuthentication(proxyAuth.username, proxyAuth.password.toCharArray)
          })
        }

        openConnection(url, Some(p.asJavaProxy))
      case _ => openConnection(url, None)
    }

    conn.asInstanceOf[HttpURLConnection]
  }

  private def writeBody(r: GenericRequest[_, R], c: HttpURLConnection): Option[OutputStream] =
    r.body match {
      case NoBody =>
        // skip
        None

      case b: BasicBodyPart =>
        val os = c.getOutputStream
        writeBasicBody(b, os)
        Some(os)

      case StreamBody(_) | MultipartStreamBody(_) =>
        // we have an instance of nothing - everything's possible!
        None

      case mp: BasicMultipartBody =>
        setMultipartBody(r, mp, c)
    }

  private def timeout(t: Duration): Int =
    if (t.isFinite) t.toMillis.toInt
    else 0

  private def writeBasicBody(body: BasicBodyPart, os: OutputStream): Unit =
    body match {
      case StringBody(b, encoding, _) =>
        val writer = new OutputStreamWriter(os, encoding)
        writer.write(b)
        // don't close - as this will close the underlying OS and cause errors
        // with multi-part
        writer.flush()

      case ByteArrayBody(b, _) =>
        os.write(b)

      case ByteBufferBody(b, _) =>
        val channel = Channels.newChannel(os)
        channel.write(b)

      case InputStreamBody(b, _) =>
        transfer(b, os)

      case FileBody(f, _) =>
        Files.copy(f.toPath, os)
    }

  private val BoundaryChars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray

  private def setMultipartBody(
      r: GenericRequest[_, R],
      mp: BasicMultipartBody,
      c: HttpURLConnection
  ): Option[OutputStream] = {
    val boundary = {
      val tlr = ThreadLocalRandom.current()
      List
        .fill(32)(BoundaryChars(tlr.nextInt(BoundaryChars.length)))
        .mkString
    }

    // inspired by: https://github.com/scalaj/scalaj-http/blob/master/src/main/scala/scalaj/http/Http.scala#L542
    val partsWithHeaders = mp.parts.map { p =>
      val contentDisposition = s"${HeaderNames.ContentDisposition}: ${p.contentDispositionHeaderValue}"
      val otherHeaders = p.headers.map(h => s"${h.name}: ${h.value}")
      val allHeaders = List(contentDisposition) ++ otherHeaders
      (allHeaders.mkString(CrLf), p)
    }

    val dashes = "--"

    val dashesLen = dashes.length.toLong
    val crLfLen = CrLf.length.toLong
    val boundaryLen = boundary.length.toLong
    val finalBoundaryLen = dashesLen + boundaryLen + dashesLen + crLfLen

    // https://stackoverflow.com/questions/31406022/how-is-an-http-multipart-content-length-header-value-calculated
    val contentLength = partsWithHeaders
      .map { case (headers, p) =>
        val bodyLen: Option[Long] = p.body match {
          case StringBody(b, encoding, _) =>
            Some(b.getBytes(encoding).length.toLong)
          case ByteArrayBody(b, _)   => Some(b.length.toLong)
          case ByteBufferBody(_, _)  => None
          case InputStreamBody(_, _) => None
          case FileBody(b, _)        => Some(b.toFile.length())
        }

        val headersLen = headers.getBytes(Utf8).length

        bodyLen.map(bl => dashesLen + boundaryLen + crLfLen + headersLen + crLfLen + crLfLen + bl + crLfLen)
      }
      .foldLeft(Option(finalBoundaryLen)) {
        case (Some(acc), Some(l)) => Some(acc + l)
        case _                    => None
      }

    val baseContentType = r.headers.find(_.is(HeaderNames.ContentType)).map(_.value).getOrElse("multipart/form-data")
    c.setRequestProperty(HeaderNames.ContentType, s"$baseContentType; boundary=" + boundary)

    contentLength.foreach { cl =>
      c.setFixedLengthStreamingMode(cl)
      c.setRequestProperty(HeaderNames.ContentLength, cl.toString)
    }

    var total = 0L

    val os = c.getOutputStream
    def writeMeta(s: String): Unit = {
      val utf8Bytes = s.getBytes(Utf8)
      os.write(utf8Bytes)
      total += utf8Bytes.length.toLong
    }

    partsWithHeaders.foreach { case (headers, p) =>
      writeMeta(dashes)
      writeMeta(boundary)
      writeMeta(CrLf)
      writeMeta(headers)
      writeMeta(CrLf)
      writeMeta(CrLf)
      writeBasicBody(p.body, os)
      writeMeta(CrLf)
    }

    // final boundary
    writeMeta(dashes)
    writeMeta(boundary)
    writeMeta(dashes)
    writeMeta(CrLf)

    Some(os)
  }

  private def readResponse[T](
      c: HttpURLConnection,
      is: InputStream,
      request: GenericRequest[T, R]
  ): Response[T] = {
    val headers = c.getHeaderFields.asScala.toVector
      .filter(_._1 != null)
      .flatMap { case (k, vv) => vv.asScala.map(Header(k, _)) }
    val contentEncoding = Option(c.getHeaderField(HeaderNames.ContentEncoding)).filter(_.nonEmpty)

    val code = StatusCode(c.getResponseCode)
    val wrappedIs =
      if (c.getRequestMethod != "HEAD" && !code.equals(StatusCode.NoContent) && !request.autoDecompressionDisabled) {
        wrapInput(contentEncoding, handleNullInput(is))
      } else handleNullInput(is)
    val responseMetadata = ResponseMetadata(code, c.getResponseMessage, headers)
    val body = bodyFromResponseAs(request.response, responseMetadata, Left(wrappedIs))

    Response(body, code, c.getResponseMessage, headers, Nil, request.onlyMetadata)
  }

  private val bodyFromResponseAs = new BodyFromResponseAs[Identity, InputStream, Nothing, Nothing]() {
    override protected def withReplayableBody(
        response: InputStream,
        replayableBody: Either[Array[Byte], SttpFile]
    ): Identity[InputStream] =
      replayableBody match {
        case Left(bytes) => new ByteArrayInputStream(bytes)
        case Right(file) => new BufferedInputStream(new FileInputStream(file.toFile))
      }
    override protected def regularIgnore(response: InputStream): Identity[Unit] = response.close()
    override protected def regularAsByteArray(response: InputStream): Identity[Array[Byte]] = toByteArray(response)
    override protected def regularAsFile(response: InputStream, file: SttpFile): Identity[SttpFile] = {
      FileHelpers.saveFile(file.toFile, response)
      file
    }
    override protected def regularAsStream(response: InputStream): (Nothing, () => Identity[Unit]) =
      throw new IllegalStateException()
    override protected def regularAsInputStream(response: InputStream): Identity[InputStream] = response
    override protected def handleWS[T](
        responseAs: GenericWebSocketResponseAs[T, _],
        meta: ResponseMetadata,
        ws: Nothing
    ): Identity[T] = ws
    override protected def cleanupWhenNotAWebSocket(response: InputStream, e: NotAWebSocketException): Identity[Unit] =
      ()
    override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): Identity[Unit] = ()
  }

  private def handleNullInput(is: InputStream): InputStream =
    if (is == null)
      new ByteArrayInputStream(Array.empty[Byte])
    else
      is

  private def wrapInput(contentEncoding: Option[String], is: InputStream): InputStream =
    contentEncoding.map(_.toLowerCase) match {
      case None                                                    => is
      case Some("gzip")                                            => new GZIPInputStream(is)
      case Some("deflate")                                         => new InflaterInputStream(is)
      case Some(ce) if customEncodingHandler.isDefinedAt((is, ce)) => customEncodingHandler(is -> ce)
      case Some(ce) =>
        throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
    }

  private def adjustExceptions[T](request: GenericRequest[_, R])(t: => T): T =
    SttpClientException.adjustExceptions(monad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  override def close(): Unit = {}
}

object HttpURLConnectionBackend {

  type EncodingHandler = PartialFunction[(InputStream, String), InputStream]

  private[client4] val defaultOpenConnection: (URL, Option[java.net.Proxy]) => URLConnection = {
    case (url, None)        => url.openConnection()
    case (url, Some(proxy)) => url.openConnection(proxy)
  }

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeConnection: HttpURLConnection => Unit = _ => (),
      createURL: String => URL = new URL(_),
      openConnection: (URL, Option[java.net.Proxy]) => URLConnection = {
        case (url, None)        => url.openConnection()
        case (url, Some(proxy)) => url.openConnection(proxy)
      },
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SyncBackend =
    wrappers.FollowRedirectsBackend(
      new HttpURLConnectionBackend(options, customizeConnection, createURL, openConnection, customEncodingHandler)
    )

  /** Create a stub backend for testing. See [[SyncBackendStub]] for details on how to configure stub responses.
    */
  def stub: SyncBackendStub = SyncBackendStub
}
