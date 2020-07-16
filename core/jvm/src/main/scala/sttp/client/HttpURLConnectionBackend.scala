package sttp.client

import java.io._
import java.net._
import java.nio.channels.Channels
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import com.github.ghik.silencer.silent
import sttp.client.internal._
import sttp.model._
import sttp.client.monad.{IdMonad, MonadError}
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocketResponse
import sttp.model.StatusCode

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

class HttpURLConnectionBackend private (
    opts: SttpBackendOptions,
    customizeConnection: HttpURLConnection => Unit,
    createURL: String => URL,
    openConnection: (URL, Option[java.net.Proxy]) => URLConnection
) extends SttpBackend[Identity, Nothing, NothingT] {
  override def send[T](r: Request[T, Nothing]): Response[T] = adjustExceptions {
    val c = openConnection(r.uri)
    c.setRequestMethod(r.method.method)
    r.headers.foreach { case Header(k, v) => c.setRequestProperty(k, v) }
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
      writeBody(r.body, c).foreach { os =>
        os.flush()
        os.close()
      }
    }

    try {
      val is = c.getInputStream
      readResponse(c, is, r.response)
    } catch {
      case e: CharacterCodingException     => throw e
      case e: UnsupportedEncodingException => throw e
      case e: SocketException              => throw e
      case _: IOException if c.getResponseCode != -1 =>
        readResponse(c, c.getErrorStream, r.response)
    }
  }

  override def openWebsocket[T, WR](
      request: Request[T, Nothing],
      handler: NothingT[WR]
  ): NothingT[WebSocketResponse[WR]] =
    handler // nothing is everything

  override val responseMonad: MonadError[Identity] = IdMonad

  private def openConnection(uri: Uri): HttpURLConnection = {
    val url = createURL(uri.toString)
    val conn = opts.proxy match {
      case Some(p) if !p.ignoreProxy(uri.host) =>
        p.auth.foreach { proxyAuth =>
          Authenticator.setDefault(new Authenticator() {
            override def getPasswordAuthentication: PasswordAuthentication = {
              new PasswordAuthentication(proxyAuth.username, proxyAuth.password.toCharArray)
            }
          })
        }

        openConnection(url, Some(p.asJavaProxy))
      case _ => openConnection(url, None)
    }

    conn.asInstanceOf[HttpURLConnection]
  }

  private def writeBody(body: RequestBody[Nothing], c: HttpURLConnection): Option[OutputStream] = {
    body match {
      case NoBody =>
        // skip
        None

      case b: BasicRequestBody =>
        val os = c.getOutputStream
        writeBasicBody(b, os)
        Some(os)

      case StreamBody(_) =>
        // we have an instance of nothing - everything's possible!
        None

      case mp: MultipartBody =>
        setMultipartBody(mp, c)
    }
  }

  private def timeout(t: Duration): Int =
    if (t.isFinite) t.toMillis.toInt
    else 0

  @silent("discarded")
  private def writeBasicBody(body: BasicRequestBody, os: OutputStream): Unit = {
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
  }

  private val BoundaryChars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray

  private def setMultipartBody(mp: MultipartBody, c: HttpURLConnection): Option[OutputStream] = {
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
      .map {
        case (headers, p) =>
          val bodyLen: Option[Long] = p.body match {
            case StringBody(b, encoding, _) =>
              Some(b.getBytes(encoding).length.toLong)
            case ByteArrayBody(b, _)   => Some(b.length.toLong)
            case ByteBufferBody(_, _)  => None
            case InputStreamBody(_, _) => None
            case FileBody(b, _)        => Some(b.toFile.length())
          }

          val headersLen = headers.getBytes(Iso88591).length

          bodyLen.map(bl => dashesLen + boundaryLen + crLfLen + headersLen + crLfLen + crLfLen + bl + crLfLen)
      }
      .foldLeft(Option(finalBoundaryLen)) {
        case (Some(acc), Some(l)) => Some(acc + l)
        case _                    => None
      }

    c.setRequestProperty(HeaderNames.ContentType, "multipart/form-data; boundary=" + boundary)

    contentLength.foreach { cl =>
      c.setFixedLengthStreamingMode(cl)
      c.setRequestProperty(HeaderNames.ContentLength, cl.toString)
    }

    var total = 0L

    val os = c.getOutputStream
    def writeMeta(s: String): Unit = {
      os.write(s.getBytes(Iso88591))
      total += s.getBytes(Iso88591).length.toLong
    }

    partsWithHeaders.foreach {
      case (headers, p) =>
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
      responseAs: ResponseAs[T, Nothing]
  ): Response[T] = {
    val headers = c.getHeaderFields.asScala.toVector
      .filter(_._1 != null)
      .flatMap { case (k, vv) => vv.asScala.map(Header.notValidated(k, _)) }
    val contentEncoding = Option(c.getHeaderField(HeaderNames.ContentEncoding))

    val code = StatusCode.notValidated(c.getResponseCode)
    val wrappedIs = if (c.getRequestMethod != "HEAD") {
      wrapInput(contentEncoding, handleNullInput(is))
    } else handleNullInput(is)
    val responseMetadata = ResponseMetadata(headers, code, c.getResponseMessage)
    val body = readResponseBody(wrappedIs, responseAs, responseMetadata)

    Response(body, code, c.getResponseMessage, headers, Nil)
  }

  private def readResponseBody[T](
      is: InputStream,
      responseAs: ResponseAs[T, Nothing],
      meta: ResponseMetadata
  ): T = {
    responseAs match {
      case MappedResponseAs(raw, g) => g(readResponseBody(is, raw, meta), meta)

      case ResponseAsFromMetadata(f) => readResponseBody(is, f(meta), meta)

      case IgnoreResponse =>
        @tailrec def consume(): Unit = if (is.read() != -1) consume()
        consume()

      case ResponseAsByteArray =>
        toByteArray(is)

      case ResponseAsStream() =>
        // only possible when the user requests the response as a stream of
        // Nothing. Oh well ...
        throw new IllegalStateException()

      case ResponseAsFile(output) =>
        FileHelpers.saveFile(output.toFile, is)
        output
    }
  }

  private def handleNullInput(is: InputStream): InputStream =
    if (is == null)
      new ByteArrayInputStream(Array.empty[Byte])
    else
      is

  private def wrapInput(contentEncoding: Option[String], is: InputStream): InputStream =
    contentEncoding.map(_.toLowerCase) match {
      case Some("gzip")    => new GZIPInputStream(is)
      case Some("deflate") => new InflaterInputStream(is)
      case _ =>               is
    }

  private def adjustExceptions[T](t: => T): T =
    SttpClientException.adjustSynchronousExceptions(t)(SttpClientException.defaultExceptionToSttpClientException)

  override def close(): Unit = {}
}

object HttpURLConnectionBackend {
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeConnection: HttpURLConnection => Unit = _ => (),
      createURL: String => URL = new URL(_),
      openConnection: (URL, Option[java.net.Proxy]) => URLConnection = {
        case (url, None)        => url.openConnection()
        case (url, Some(proxy)) => url.openConnection(proxy)
      }
  ): SttpBackend[Identity, Nothing, NothingT] =
    new FollowRedirectsBackend[Identity, Nothing, NothingT](
      new HttpURLConnectionBackend(options, customizeConnection, createURL, openConnection)
    )

  /**
    * Create a stub backend for testing, which uses the [[Identity]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Identity, Nothing] = SttpBackendStub.synchronous
}
