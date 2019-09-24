package sttp.client

import java.io.ByteArrayInputStream

import sttp.client.curl.CurlApi._
import sttp.client.curl.CurlCode.CurlCode
import sttp.client.curl.CurlInfo._
import sttp.client.curl.CurlOption.{Header => _, _}
import sttp.client.curl._
import sttp.client.internal._
import sttp.model._
import sttp.client.monad.MonadError
import sttp.model.{Header, Method, StatusCode}

import scala.collection.immutable.Seq
import scala.io.Source
import scala.scalanative.native
import scala.scalanative.native.stdlib._
import scala.scalanative.native.string._
import scala.scalanative.native.{CSize, Ptr, _}

abstract class AbstractCurlBackend[R[_], S](monad: MonadError[R], verbose: Boolean) extends SttpBackend[R, S] {

  override val responseMonad: MonadError[R] = monad

  override def close(): R[Unit] = monad.unit(())

  private var headers: CurlList = _
  private var multiPartHeaders: Seq[CurlList] = Seq()

  override def send[T](request: Request[T, S]): R[Response[T]] = native.Zone { implicit z =>
    val curl = CurlApi.init
    if (verbose) {
      curl.option(Verbose, true)
    }
    if (request.tags.nonEmpty) {
      responseMonad.error(new UnsupportedOperationException("Tags are not supported"))
    }
    val reqHeaders = request.headers
    if (reqHeaders.size > 0) {
      reqHeaders.find(_.name == "Accept-Encoding").foreach(h => curl.option(AcceptEncoding, h.value))
      request.body match {
        case _: MultipartBody =>
          headers = transformHeaders(reqHeaders :+ Header("Content-Type", "multipart/form-data"))
        case _ =>
          headers = transformHeaders(reqHeaders)
      }
      curl.option(HttpHeader, headers.ptr)
    }

    val spaces = responseSpace
    curl.option(WriteFunction, AbstractCurlBackend.wdFunc)
    curl.option(WriteData, spaces.bodyResp)
    curl.option(HeaderData, spaces.headersResp)
    curl.option(Url, request.uri.toString)
    curl.option(TimeoutMs, request.options.readTimeout.toMillis)
    setMethod(curl, request.method)

    setRequestBody(curl, request.body)

    responseMonad.flatMap(lift(curl.perform)) { _ =>
      curl.info(ResponseCode, spaces.httpCode)
      val responseBody = fromCString(!spaces.bodyResp._1)
      val responseHeaders_ = parseHeaders(fromCString(!spaces.headersResp._1))
      val httpCode = StatusCode((!spaces.httpCode).toInt)

      if (headers.ptr != null) headers.ptr.free()
      multiPartHeaders.foreach(_.ptr.free())
      free(!spaces.bodyResp._1)
      free(!spaces.headersResp._1)
      free(spaces.bodyResp.cast[Ptr[CSignedChar]])
      free(spaces.headersResp.cast[Ptr[CSignedChar]])
      free(spaces.httpCode.cast[Ptr[CSignedChar]])
      curl.cleanup()

      val statusText = responseHeaders_.head.name.split(" ").last
      val responseHeaders = responseHeaders_.tail
      val responseMetadata = ResponseMetadata(responseHeaders, httpCode, statusText)

      val body: R[T] = readResponseBody(responseBody, request.response, responseMetadata)
      responseMonad.map(body) { b =>
        Response[T](
          body = b,
          code = httpCode,
          statusText = statusText,
          headers = responseHeaders,
          history = Nil
        )
      }
    }
  }

  private def setMethod(handle: CurlHandle, method: Method)(implicit z: Zone): R[CurlCode] = {
    val m = method match {
      case Method.GET     => handle.option(HttpGet, true)
      case Method.HEAD    => handle.option(Head, true)
      case Method.POST    => handle.option(Post, true)
      case Method.PUT     => handle.option(Put, true)
      case Method.DELETE  => handle.option(Post, true)
      case Method.OPTIONS => handle.option(RtspRequest, true)
      case Method.PATCH   => handle.option(CustomRequest, "PATCH")
      case Method.CONNECT => handle.option(ConnectOnly, true)
      case Method.TRACE   => handle.option(CustomRequest, "TRACE")
    }
    lift(m)
  }

  private def setRequestBody(curl: CurlHandle, body: RequestBody[S])(implicit zone: Zone): R[CurlCode] =
    body match { // todo: assign to responseMonad object
      case b: BasicRequestBody =>
        val str = basicBodyToString(b)
        lift(curl.option(PostFields, toCString(str)))
      case MultipartBody(parts) =>
        val mime = curl.mime
        parts.foreach {
          case Multipart(name, partBody, fileName, contentType, additionalHeaders) =>
            val part = mime.addPart()
            part.withName(name)
            val str = basicBodyToString(partBody)
            part.withData(str)
            if (fileName.isDefined) {
              part.withFileName(fileName.get)
            }
            if (contentType.isDefined) {
              part.withMimeType(contentType.get)
            }
            if (additionalHeaders.nonEmpty) {
              val curlList = transformHeaders(additionalHeaders)
              part.withHeaders(curlList.ptr)
              multiPartHeaders = multiPartHeaders :+ curlList
            }
        }
        lift(curl.option(Mimepost, mime))
      case StreamBody(_) =>
        responseMonad.error(new IllegalStateException("CurlBackend does not support stream request body"))
      case NoBody =>
        responseMonad.unit(CurlCode.Ok)
    }

  private def basicBodyToString(body: BasicRequestBody): String =
    body match {
      case StringBody(b, _, _)   => b
      case ByteArrayBody(b, _)   => new String(b)
      case ByteBufferBody(b, _)  => new String(b.array)
      case InputStreamBody(b, _) => Source.fromInputStream(b).mkString
      case FileBody(f, _)        => Source.fromFile(f.toFile).mkString
    }

  private def responseSpace: CurlSpaces = {
    val bodyResp = malloc(sizeof[CurlFetch]).cast[Ptr[CurlFetch]]
    !bodyResp._1 = calloc(4096, sizeof[CChar]).cast[CString]
    !bodyResp._2 = 0
    val headersResp = malloc(sizeof[CurlFetch]).cast[Ptr[CurlFetch]]
    !headersResp._1 = calloc(4096, sizeof[CChar]).cast[CString]
    !headersResp._2 = 0
    val httpCode = malloc(sizeof[Long]).cast[Ptr[Long]]
    new CurlSpaces(bodyResp, headersResp, httpCode)
  }

  private def parseHeaders(str: String): Seq[Header] = {
    val array = str
      .split("\n")
      .filter(_.trim.length > 0)
      .map { line =>
        val split = line.split(":", 2)
        if (split.size == 2)
          Header(split(0).trim, split(1).trim)
        else
          Header(split(0).trim, "")
      }
    Seq.from(array: _*)
  }

  private def readResponseBody[T](
      response: String,
      responseAs: ResponseAs[T, S],
      responseMetadata: ResponseMetadata
  ): R[T] = {

    responseAs match {
      case MappedResponseAs(raw, g) =>
        responseMonad.map(readResponseBody(response, raw, responseMetadata))(g(_, responseMetadata))
      case ResponseAsFromMetadata(f) => readResponseBody(response, f(responseMetadata), responseMetadata)
      case IgnoreResponse            => responseMonad.unit((): Unit)
      case ResponseAsByteArray       => toByteArray(response)
      case ResponseAsFile(output, overwrite) =>
        responseMonad.map(toByteArray(response)) { a =>
          val is = new ByteArrayInputStream(a)
          val f = FileHelpers.saveFile(output.toFile, is, overwrite)
          SttpFile.fromFile(f)
        }
      case ResponseAsStream() =>
        responseMonad.error(new IllegalStateException("CurlBackend does not support streaming responses"))
    }
  }

  private def transformHeaders(reqHeaders: Iterable[Header])(implicit z: Zone): CurlList = {
    reqHeaders
      .map {
        case Header(headerName, headerValue) =>
          s"$headerName: $headerValue"
      }
      .foldLeft(new CurlList(null)) {
        case (acc, h) =>
          new CurlList(acc.ptr.append(h))
      }
  }

  private def toByteArray(str: String): R[Array[Byte]] = responseMonad.unit(str.toCharArray.map(_.toByte))

  private def lift(code: CurlCode): R[CurlCode] = {
    code match {
      case CurlCode.Ok => responseMonad.unit(code)
      case _           => responseMonad.error(new RuntimeException(s"Command failed with status $code"))
    }
  }
}

object AbstractCurlBackend {
  def writeData(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[CurlFetch]): CSize = {
    val index: CSize = !data._2
    val increment: CSize = size * nmemb
    !data._2 = !data._2 + increment
    !data._1 = realloc(!data._1, !data._2 + 1)
    memcpy(!data._1 + index, ptr, increment)
    !(!data._1).+(!data._2) = 0.toByte
    size * nmemb
  }

  val wdFunc = CFunctionPtr.fromFunction4(writeData)
}
