package sttp.client4.curl

import sttp.capabilities.Effect
import sttp.client4.curl.internal.CurlApi._
import sttp.client4.curl.internal.CurlCode.CurlCode
import sttp.client4.curl.internal.CurlInfo._
import sttp.client4.curl.internal.CurlMCode
import sttp.client4.curl.internal.CurlOption.{Header => _, _}
import sttp.client4.curl.internal._
import sttp.client4.internal._
import sttp.client4._
import sttp.client4.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.{ByteArrayInputStream, InputStream}

import scala.collection.immutable.Seq
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.scalanative.libc.stdio.{fclose, fopen, FILE}
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.scalanative.unsafe.{CSize, Ptr, _}
import scala.scalanative.unsigned._

abstract class AbstractCurlBackend[F[_]](_monad: MonadError[F], verbose: Boolean) extends GenericBackend[F, Any] {
  override implicit def monad: MonadError[F] = _monad

  /** Given a [[CurlHandle]], perform the request and return a [[CurlCode]]. */
  protected def performCurl(c: CurlHandle): F[CurlCode]

  /** Same as [[performCurl]], but also checks and throws runtime exceptions on bad [[CurlCode]]s. */
  private final def perform(c: CurlHandle) = performCurl(c).flatMap(lift)

  type R = Any with Effect[F]

  override def close(): F[Unit] = monad.unit(())

  /** A request-specific context, with allocated zones and headers. */
  private class Context() {
    implicit val zone: Zone = Zone.open()
    private val headers = ArrayBuffer[CurlList]()

    /** Create a new Headers list that gets cleaned up when the context is destroyed. */
    def transformHeaders(reqHeaders: Iterable[Header]): CurlList = {
      val h = reqHeaders
        .map(header => s"${header.name}: ${header.value}")
        .foldLeft(new CurlList(null)) { case (acc, h) =>
          new CurlList(acc.ptr.append(h))
        }
      headers += h
      h
    }

    /** Detach headers from this context, transferring cleanup responsibility to the caller.
      *
      * After this call, close() will no longer free the returned headers — the caller must do so. Used in the streaming
      * path to transfer header slist ownership to CurlMultiInputStream, ensuring headers outlive the transfer as
      * required by libcurl's CURLOPT_HTTPHEADER contract.
      */
    def detachHeaders(): Seq[CurlList] = {
      val h = headers.toSeq
      headers.clear()
      h
    }

    def close() = {
      zone.close()
      headers.foreach(l => if (l.ptr != null) l.ptr.free())
    }
  }

  private object Context {

    /** Create a new context and evaluates the body with it. Closes the context at the end.
      *
      * Note: the .ensure() extension method (from sttp.monad.syntax) calls ensure2, which takes f by-name. This means
      * the try/finally in IdentityMonad.ensure2 properly wraps body(ctx), so ctx.close() runs even on exception.
      */
    def evaluateUsing[T](body: Context => F[T]): F[T] = {
      implicit val ctx = new Context()
      body(ctx).ensure(monad.eval(ctx.close()))
    }
  }

  override def send[T](request: GenericRequest[T, R]): F[Response[T]] =
    adjustExceptions(request) {
      def perform(implicit ctx: Context): F[Response[T]] = {
        implicit val z = ctx.zone
        val curl = CurlApi.init
        if (verbose) {
          curl.option(Verbose, parameter = true)
        }
        if (request.attributes.nonEmpty) {
          return monad.error(new UnsupportedOperationException("Attributes are not supported"))
        }

        val reqHeaders = collection.mutable.ListBuffer[Header](request.headers: _*)

        request.body match {
          case _: MultipartBody[_] =>
            reqHeaders += Header.contentType(MediaType.MultipartFormData)
          case _ =>
        }

        val userAgent =
          reqHeaders.find(_.is(HeaderNames.UserAgent)) match {
            case Some(h) => h.value
            case None    => curlUserAgent
          }

        curl.option(
          UserAgent,
          userAgent
        )

        if (reqHeaders.nonEmpty) {
          reqHeaders.find(_.name == "Accept-Encoding").foreach(h => curl.option(AcceptEncoding, h.value))

          val curlHeaders = ctx.transformHeaders(reqHeaders)

          curl.option(HttpHeader, curlHeaders.ptr)
        }

        val spaces = responseSpace
        FileHelpers.getFilePath(request.response.delegate) match {
          case Some(file) => handleFile(request, curl, file, spaces)
          case None if InputStreamResponseDetector.containsInputStreamResponse(request.response.delegate) =>
            handleBaseWithMulti(request, curl, spaces)
          case None => handleBase(request, curl, spaces)
        }
      }

      Context.evaluateUsing(ctx => perform(ctx))
    }

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(monad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  private def handleBase[T](request: GenericRequest[T, R], curl: CurlHandle, spaces: CurlSpaces)(implicit
      ctx: Context
  ) = {
    implicit val z = ctx.zone
    curl.option(WriteFunction, AbstractCurlBackend.wdFunc)
    curl.option(WriteData, spaces.bodyResp)
    curl.option(TimeoutMs, request.options.readTimeout.toMillis)
    curl.option(HeaderData, spaces.headersResp)
    curl.option(Url, request.uri.toString)
    setMethod(curl, request.method)
    setRequestBody(curl, request.body, request.method)
    monad.flatMap(perform(curl)) { _ =>
      curl.info(ResponseCode, spaces.httpCode)
      val responseBody = fromCString((!spaces.bodyResp)._1)
      val (statusText, responseHeaders) = parseHeadersAndStatus(fromCString((!spaces.headersResp)._1))
      val httpCode = StatusCode((!spaces.httpCode).toInt)
      free((!spaces.bodyResp)._1)
      free((!spaces.headersResp)._1)
      free(spaces.bodyResp.asInstanceOf[Ptr[CSignedChar]])
      free(spaces.headersResp.asInstanceOf[Ptr[CSignedChar]])
      free(spaces.httpCode.asInstanceOf[Ptr[CSignedChar]])
      curl.cleanup()

      val responseMetadata = ResponseMetadata(httpCode, statusText, responseHeaders)

      // the response is read into memory
      request.options.onBodyReceived(responseMetadata)

      val body: F[T] = bodyFromResponseAs(request.response, responseMetadata, Left(responseBody))
      monad.map(body) { b =>
        Response[T](
          body = b,
          code = httpCode,
          statusText = statusText,
          headers = responseHeaders,
          history = Nil,
          request = request.onlyMetadata
        )
      }
    }
  }

  private def handleFile[T](request: GenericRequest[T, R], curl: CurlHandle, file: SttpFile, spaces: CurlSpaces)(
      implicit ctx: Context
  ) = {
    implicit val z = ctx.zone
    val outputPath = file.toPath.toString
    val outputFilePtr: Ptr[FILE] = fopen(toCString(outputPath), toCString("wb"))
    curl.option(WriteData, outputFilePtr)
    curl.option(Url, request.uri.toString)
    setMethod(curl, request.method)
    setRequestBody(curl, request.body, request.method)
    monad.flatMap(perform(curl)) { _ =>
      curl.info(ResponseCode, spaces.httpCode)
      val httpCode = StatusCode((!spaces.httpCode).toInt)
      free(spaces.httpCode.asInstanceOf[Ptr[CSignedChar]])
      fclose(outputFilePtr)
      curl.cleanup()
      val responseMetadata = ResponseMetadata(httpCode, "", List.empty)
      val body: F[T] = bodyFromResponseAs(request.response, responseMetadata, Left(outputPath))
      monad.map(body) { b =>
        Response[T](
          body = b,
          code = httpCode,
          statusText = "",
          headers = List(Header.contentLength(file.size)),
          history = Nil,
          request = request.onlyMetadata
        )
      }
    }
  }

  private object InputStreamResponseDetector {

    /** Recursively checks if a response descriptor contains any InputStream response types. This inspects through
      * MappedResponseAs, ResponseAsFromMetadata conditions, and ResponseAsBoth. Returns true if ANY branch might produce
      * an InputStream response.
      */
    def containsInputStreamResponse(delegate: GenericResponseAs[_, _]): Boolean =
      delegate match {
        case ResponseAsInputStream(_) | ResponseAsInputStreamUnsafe => true
        case MappedResponseAs(raw, _, _)                           => containsInputStreamResponse(raw)
        case rfm: ResponseAsFromMetadata[_, _] =>
          containsInputStreamResponse(rfm.default) ||
            rfm.conditions.exists(c => containsInputStreamResponse(c.responseAs))
        case ResponseAsBoth(l, r) =>
          containsInputStreamResponse(l) || containsInputStreamResponse(r)
        case _ => false
      }
  }

  private def handleBaseWithMulti[T](request: GenericRequest[T, R], curl: CurlHandle, spaces: CurlSpaces)(implicit
      ctx: Context
  ): F[Response[T]] = {
    implicit val z = ctx.zone
    curl.option(WriteFunction, AbstractCurlBackend.wdFunc)
    curl.option(WriteData, spaces.bodyResp)
    curl.option(TimeoutMs, request.options.readTimeout.toMillis)
    curl.option(HeaderData, spaces.headersResp)
    curl.option(Url, request.uri.toString)
    setMethod(curl, request.method)
    setRequestBody(curl, request.body, request.method).flatMap { _ =>
      val multi = CurlApi.multiInit
      liftM(multi.addHandle(curl)).flatMap { _ =>
        // Track whether native resources (multi, curl, spaces) have already been cleaned up —
        // either by transferring ownership to CurlMultiInputStream or by explicit freeing in a fallback path.
        // When true, the outer catch block must NOT attempt to free them (would double-free).
        var resourcesCleaned = false
        try {
          // Phase 1: Drive until headers are received
          driveUntilHeaders(multi, curl)

          // Read response metadata
          curl.info(ResponseCode, spaces.httpCode)
          val httpCode = StatusCode((!spaces.httpCode).toInt)

          // Check for transfer-level failure (connection refused, DNS error, etc.).
          // curl_multi_perform returns CURLM_OK even when individual transfers fail;
          // the failure is only visible via httpCode==0 and curl_multi_info_read.
          if (httpCode == StatusCode(0)) {
            val transferResult = multi.infoReadResult(null)
            throw new RuntimeException(
              s"Request to ${request.uri} failed: no HTTP response received" +
                (if (transferResult > 0) s" (CURLcode $transferResult)" else "")
            )
          }

          val (statusText, responseHeaders) = parseHeadersAndStatus(fromCString((!spaces.headersResp)._1))
          val responseMetadata = ResponseMetadata(httpCode, statusText, responseHeaders)
          request.options.onBodyReceived(responseMetadata)

          // Phase 2: Resolve actual response type and dispatch
          val resolvedAs = resolveResponseAs(request.response.delegate, responseMetadata)

          if (isDirectInputStreamResponse(resolvedAs)) {
            // TRUE STREAMING PATH
            // Transfer ownership of handles+buffers AND header slists to the InputStream.
            // Detach headers from Context so they survive past Context.close() — libcurl's
            // CURLOPT_HTTPHEADER contract requires the slist to outlive the transfer.
            val detachedHeaders = ctx.detachHeaders()
            val inputStream = new CurlMultiInputStream(
              multi,
              curl,
              spaces.bodyResp,
              spaces.headersResp,
              spaces.httpCode,
              detachedHeaders
            )
            resourcesCleaned = true
            try {
              val body: F[T] = dispatchInputStreamResponse(
                request.response.delegate,
                inputStream,
                responseMetadata
              )
              monad.map(body) { b =>
                Response[T](
                  body = b,
                  code = httpCode,
                  statusText = statusText,
                  headers = responseHeaders,
                  history = Nil,
                  request = request.onlyMetadata
                )
              }
            } catch {
              case e: Throwable =>
                inputStream.close() // frees multi, curl, spaces, AND headers
                throw e
            }
          } else {
            // NON-STREAMING FALLBACK (e.g., error branch of asEither)
            // Drain remaining body, then handle normally
            driveToCompletion(multi)

            val responseBody = fromCString((!spaces.bodyResp)._1)
            // Clean up multi resources
            val _ = multi.removeHandle(curl)
            multi.cleanup()
            curl.cleanup()
            free((!spaces.bodyResp)._1)
            free((!spaces.headersResp)._1)
            free(spaces.bodyResp.asInstanceOf[Ptr[CSignedChar]])
            free(spaces.headersResp.asInstanceOf[Ptr[CSignedChar]])
            free(spaces.httpCode.asInstanceOf[Ptr[CSignedChar]])
            resourcesCleaned = true // prevent double-free in outer catch

            val body: F[T] = bodyFromResponseAs(request.response, responseMetadata, Left(responseBody))
            monad.map(body) { b =>
              Response[T](
                body = b,
                code = httpCode,
                statusText = statusText,
                headers = responseHeaders,
                history = Nil,
                request = request.onlyMetadata
              )
            }
          }
        } catch {
          case e: Throwable if !resourcesCleaned =>
            // Clean up multi + curl + spaces on failure before ownership was transferred
            val _ = multi.removeHandle(curl)
            multi.cleanup()
            curl.cleanup()
            free((!spaces.bodyResp)._1)
            free((!spaces.headersResp)._1)
            free(spaces.bodyResp.asInstanceOf[Ptr[CSignedChar]])
            free(spaces.headersResp.asInstanceOf[Ptr[CSignedChar]])
            free(spaces.httpCode.asInstanceOf[Ptr[CSignedChar]])
            throw e
        }
      }
    }
  }

  /** Drives the curl_multi transfer until response headers are available (HTTP status code > 0) or the transfer
    * completes entirely (for very small responses).
    */
  private def driveUntilHeaders(
      multi: CurlMultiHandle,
      curl: CurlHandle
  )(implicit z: Zone): Unit = {
    val runningHandles: Ptr[CInt] = alloc[CInt](1)
    !runningHandles = 1
    val code: Ptr[Long] = alloc[Long](1)
    !code = 0L

    while (!runningHandles > 0) {
      val mc = multi.perform(runningHandles)
      if (mc != CurlMCode.Ok)
        throw new RuntimeException(s"curl_multi_perform failed: $mc")

      // Check if response code is available (headers received)
      val _ = curl.info(ResponseCode, code)
      if (!code > 0L) return // headers received

      if (!runningHandles == 0) return // transfer complete (very small response)

      // Wait for activity
      val pc = multi.poll(1000, null)
      if (pc != CurlMCode.Ok)
        throw new RuntimeException(s"curl_multi_poll failed in driveUntilHeaders: $pc")
    }
  }

  /** Drains the remaining response body by driving curl_multi to completion. Used for the non-streaming fallback path
    * (e.g., error responses).
    */
  private def driveToCompletion(multi: CurlMultiHandle)(implicit z: Zone): Unit = {
    val runningHandles: Ptr[CInt] = alloc[CInt](1)
    !runningHandles = 1

    while (!runningHandles > 0) {
      val mc = multi.perform(runningHandles)
      if (mc != CurlMCode.Ok)
        throw new RuntimeException(s"curl_multi_perform failed in driveToCompletion: $mc")
      if (!runningHandles > 0) {
        val pc = multi.poll(1000, null)
        if (pc != CurlMCode.Ok)
          throw new RuntimeException(s"curl_multi_poll failed in driveToCompletion: $pc")
      }
    }
  }

  /** Recursively resolves ResponseAsFromMetadata and MappedResponseAs to find the concrete "leaf" response type,
    * evaluating metadata conditions along the way.
    */
  private def resolveResponseAs[T](
      delegate: GenericResponseAs[T, _],
      meta: ResponseMetadata
  ): GenericResponseAs[_, _] = delegate match {
    case rfm: ResponseAsFromMetadata[_, _] => resolveResponseAs(rfm(meta), meta)
    case MappedResponseAs(raw, _, _)       => resolveResponseAs(raw, meta)
    case other                             => other
  }

  /** Checks whether a resolved (leaf) response type is a direct InputStream response. */
  private def isDirectInputStreamResponse(resolved: GenericResponseAs[_, _]): Boolean =
    resolved match {
      case ResponseAsInputStream(_) | ResponseAsInputStreamUnsafe => true
      case _                                                      => false
    }

  /** Dispatches an InputStream through the response type chain, applying MappedResponseAs transformations and resolving
    * ResponseAsFromMetadata conditions. This handles the streaming path where we provide a CurlMultiInputStream instead
    * of a pre-buffered String.
    */
  private def dispatchInputStreamResponse[T](
      delegate: GenericResponseAs[T, _],
      is: InputStream,
      meta: ResponseMetadata
  ): F[T] =
    delegate match {
      case ResponseAsInputStreamUnsafe =>
        monad.unit(is.asInstanceOf[T])

      case ResponseAsInputStream(f) =>
        monad
          .eval(f.asInstanceOf[InputStream => Any](is))
          .ensure(monad.eval(is.close()))
          .asInstanceOf[F[T]]

      case MappedResponseAs(raw, g, _) =>
        dispatchInputStreamResponse(raw, is, meta).flatMap { result =>
          monad.eval(g.asInstanceOf[(Any, ResponseMetadata) => Any](result, meta)).asInstanceOf[F[T]]
        }

      case rfm: ResponseAsFromMetadata[T, _] @unchecked =>
        dispatchInputStreamResponse(rfm(meta), is, meta)

      case ResponseAsBoth(l, _) =>
        // An InputStream is not replayable, so the right side gets None — consistent with
        // how BodyFromResponseAs handles non-replayable bodies in the standard path.
        monad.map(dispatchInputStreamResponse(l, is, meta)) { lr =>
          (lr, None).asInstanceOf[T]
        }

      case _ =>
        // Fallback: drain stream to string and use standard path
        val bytes = readInputStreamToByteArray(is)
        is.close()
        val str = new String(bytes)
        bodyFromResponseAs(ResponseAs(delegate.asInstanceOf[GenericResponseAs[T, Any]]), meta, Left(str))
    }

  private def readInputStreamToByteArray(is: InputStream): Array[Byte] = {
    val buffer = new java.io.ByteArrayOutputStream()
    val chunk = new Array[Byte](8192)
    var n = is.read(chunk)
    while (n != -1) {
      buffer.write(chunk, 0, n)
      n = is.read(chunk)
    }
    buffer.toByteArray
  }

  private def liftM(code: CurlMCode.CurlMCode): F[CurlMCode.CurlMCode] =
    code match {
      case CurlMCode.Ok => monad.unit(code)
      case _            => monad.error(new RuntimeException(s"curl_multi operation failed: $code"))
    }

  private def setMethod(handle: CurlHandle, method: Method)(implicit z: Zone): F[CurlCode] = {
    val m = method match {
      case Method.GET     => handle.option(HttpGet, true)
      case Method.HEAD    => handle.option(Head, true)
      case Method.POST    => handle.option(Post, true)
      case Method.PUT     => handle.option(CustomRequest, "PUT")
      case Method.DELETE  => handle.option(CustomRequest, "DELETE")
      case Method.OPTIONS => handle.option(RtspRequest, true)
      case Method.PATCH   => handle.option(CustomRequest, "PATCH")
      case Method.CONNECT => handle.option(ConnectOnly, true)
      case Method.TRACE   => handle.option(CustomRequest, "TRACE")
      case Method(m)      => handle.option(CustomRequest, m)
    }
    lift(m)
  }

  private def setRequestBody(curl: CurlHandle, body: GenericRequestBody[R], method: Method)(implicit
      ctx: Context
  ): F[CurlCode] = {
    implicit val z = ctx.zone
    body match { // todo: assign to monad object
      case b: BasicBodyPart =>
        val str = basicBodyToString(b)
        lift(curl.option(PostFields, toCString(str)))
      case m: MultipartBody[R] =>
        val mime = curl.mime
        m.parts.foreach { case p @ Part(name, partBody, _, headers) =>
          val part = mime.addPart()
          part.withName(name)
          val str = basicBodyToString(partBody)
          part.withData(str)
          p.fileName.foreach(part.withFileName(_))
          p.contentType.foreach(part.withMimeType(_))

          val otherHeaders = headers.filterNot(_.is(HeaderNames.ContentType))
          if (otherHeaders.nonEmpty) {
            val curlList = ctx.transformHeaders(otherHeaders)
            part.withHeaders(curlList.ptr)
          }
        }
        lift(curl.option(Mimepost, mime))
      case StreamBody(_) =>
        monad.error(new IllegalStateException("CurlBackend does not support stream request body"))
      case NoBody =>
        // POST with empty body might wait for the input on stdin
        if (method.is(Method.POST)) lift(curl.option(PostFields, c""))
        else monad.unit(CurlCode.Ok)
    }
  }

  private def basicBodyToString(body: BodyPart[_]): String =
    body match {
      case StringBody(b, _, _)   => b
      case ByteArrayBody(b, _)   => new String(b)
      case ByteBufferBody(b, _)  => new String(b.array)
      case InputStreamBody(b, _) => Source.fromInputStream(b).mkString
      case FileBody(f, _)        => Source.fromFile(f.toFile).mkString
      case _                     => throw new IllegalArgumentException(s"Unsupported body: $body")
    }

  private def responseSpace: CurlSpaces = {
    val bodyResp = malloc(sizeof[CurlFetch]).asInstanceOf[Ptr[CurlFetch]]
    (!bodyResp)._1 = calloc(4096.toUInt, sizeof[CChar])
    (!bodyResp)._2 = 0.toUInt
    val headersResp = malloc(sizeof[CurlFetch]).asInstanceOf[Ptr[CurlFetch]]
    (!headersResp)._1 = calloc(4096.toUInt, sizeof[CChar])
    (!headersResp)._2 = 0.toUInt
    val httpCode = malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
    new CurlSpaces(bodyResp, headersResp, httpCode)
  }

  /** Parses raw header string from curl into a status line and header list.
    *
    * Returns (statusText, headers). The first line is expected to be the HTTP status line (e.g. "HTTP/1.1 200 OK").
    * The status text is extracted as everything after the status code (the part after the second space), which may be
    * empty (HTTP/2 responses often omit the reason phrase). If headers are empty (e.g. connection failed before any
    * response), returns ("", Nil).
    */
  private def parseHeadersAndStatus(str: String): (String, Seq[Header]) = {
    val lines = str.split("\n").filter(_.trim.nonEmpty)
    if (lines.isEmpty) return ("", Nil)

    val statusLine = lines.head.trim
    // Status line format: "HTTP/1.1 200 OK" or "HTTP/2 200"
    // Extract status text = everything after "HTTP/x.y <code> ", if present
    val statusText = {
      val firstSpace = statusLine.indexOf(' ')
      if (firstSpace < 0) ""
      else {
        val secondSpace = statusLine.indexOf(' ', firstSpace + 1)
        if (secondSpace < 0) ""
        else statusLine.substring(secondSpace + 1).trim
      }
    }

    val headers = Seq(lines.tail: _*).map { line =>
      val split = line.split(":", 2)
      if (split.size == 2)
        Header(split(0).trim, split(1).trim)
      else
        Header(split(0).trim, "")
    }

    (statusText, headers)
  }

  private lazy val curlUserAgent = "sttp-curl/" + fromCString(CCurl.getVersion())

  private lazy val bodyFromResponseAs = new BodyFromResponseAs[F, String, Nothing, Nothing] {
    override protected def withReplayableBody(
        response: String,
        replayableBody: Either[Array[Byte], SttpFile]
    ): F[String] = response.unit

    override protected def regularIgnore(response: String): F[Unit] = ().unit

    override protected def regularAsByteArray(response: String): F[Array[Byte]] = toByteArray(response)

    override protected def regularAsFile(response: String, file: SttpFile): F[SttpFile] =
      monad.unit(file)

    override protected def regularAsInputStream(response: String): F[InputStream] =
      monad.unit(new ByteArrayInputStream(response.getBytes))

    override protected def regularAsStream(response: String): F[(Nothing, () => F[Unit])] =
      throw new IllegalStateException("CurlBackend does not support streaming responses")

    override protected def handleWS[T](
        responseAs: GenericWebSocketResponseAs[T, _],
        meta: ResponseMetadata,
        ws: Nothing
    ): F[T] = ws

    override protected def cleanupWhenNotAWebSocket(
        response: String,
        e: NotAWebSocketException
    ): F[Unit] = ().unit

    override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): F[Unit] = response
  }

  private def toByteArray(str: String): F[Array[Byte]] = monad.unit(str.getBytes)

  private def lift(code: CurlCode): F[CurlCode] =
    code match {
      case CurlCode.Ok => monad.unit(code)
      case _           => monad.error(new RuntimeException(s"Command failed with status $code"))
    }
}

/** Curl backends that performs the curl operation with a simple `curl_easy_perform`. */
abstract class AbstractSyncCurlBackend[F[_]](_monad: MonadError[F], verbose: Boolean)
    extends AbstractCurlBackend[F](_monad, verbose) {
  override def performCurl(c: CurlHandle): F[CurlCode.CurlCode] = monad.unit(c.perform)
}

object AbstractCurlBackend {
  val wdFunc: CFuncPtr4[Ptr[Byte], CSize, CSize, Ptr[CurlFetch], CSize] = {
    (ptr: Ptr[CChar], size: CSize, nmemb: CSize, data: Ptr[CurlFetch]) =>
      val index: CSize = (!data)._2
      val increment: CSize = size * nmemb
      (!data)._2 = (!data)._2 + increment
      (!data)._1 = realloc((!data)._1, (!data)._2 + 1.toUInt)
      val _ = memcpy((!data)._1 + index, ptr, increment)
      !(!data)._1.+((!data)._2) = 0.toByte
      size * nmemb
  }
}
