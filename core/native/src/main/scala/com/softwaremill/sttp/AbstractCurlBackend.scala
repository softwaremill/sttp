package com.softwaremill.sttp

import java.io.ByteArrayInputStream

import com.softwaremill.sttp.curl.CurlInfo._
import com.softwaremill.sttp.curl._
import com.softwaremill.sttp.curl.CurlOption._
import com.softwaremill.sttp.curl.CurlApi._
import com.softwaremill.sttp.curl.CurlCode.CurlCode
import com.softwaremill.sttp.internal.SttpFile

import scala.collection.immutable.Seq
import scala.scalanative.native
import scala.scalanative.native.stdlib._
import scala.scalanative.native.string._
import scala.scalanative.native.{CSize, Ptr, _}

abstract class AbstractCurlBackend[R[_], S](rm: MonadError[R], verbose: Boolean = false) extends SttpBackend[R, S] {

  override val responseMonad: MonadError[R] = rm

  override def close(): Unit = {}

  private var slist: CurlList = _

  override def send[T](request: Request[T, S]): R[Response[T]] = native.Zone { implicit z =>
    val curl = CurlApi.init
    curl.option(Verbose, verbose)
    if (request.tags.nonEmpty) {
      responseMonad.error(new UnsupportedOperationException("Tags are not supported"))
    }
    if (request.headers.size > 0) {
      slist = request.headers
        .map { case (headerName, headerValue) => s"$headerName: $headerValue" }
        .foldLeft(new CurlList(null)) {
          case (acc, h) =>
            new CurlList(CurlApi.slistAppend(acc.ptr, h))
        }
      curl.option(HttpHeader, slist.ptr)
    }

    val spaces = responseSpace
    curl.option(WriteFunction, AbstractCurlBackend.wdFunc)
    curl.option(WriteData, spaces.bodyResp)
    curl.option(HeaderData, spaces.headersResp)
    curl.option(Url, request.uri.toString)
    curl.option(TimeoutMs, request.options.readTimeout.toMillis)
    setMethod(curl, request.method)

    curl.perform

    curl.info(ResponseCode, spaces.httpCode)
    val responseBody = fromCString(!spaces.bodyResp._1)
    val responseHeaders = parseHeaders(fromCString(!spaces.headersResp._1))
    val httpCode = (!spaces.httpCode).toInt

    CurlApi.slistFree(slist.ptr)
    free(!spaces.bodyResp._1)
    free(!spaces.headersResp._1)
    free(spaces.bodyResp.cast[Ptr[Byte]])
    free(spaces.headersResp.cast[Ptr[Byte]])
    free(spaces.httpCode.cast[Ptr[Byte]])
    curl.cleanup()

    val body: R[Either[Array[Byte], T]] = if (StatusCodes.isSuccess(httpCode)) {
      responseMonad.map(readResponseBody(responseBody, request.response))(Right.apply)
    } else {
      responseMonad.map(toByteArray(responseBody))(Left.apply)
    }
    responseMonad.map(body) { b =>
      Response[T](
        rawErrorBody = b,
        code = httpCode,
        statusText = responseHeaders.head._1.split(" ").last,
        headers = responseHeaders.tail,
        history = Nil
      )
    }
  }

  private def responseSpace: CurlSpaces = {
    val bodyResp = malloc(sizeof[CurlFetch]).cast[Ptr[CurlFetch]]
    !bodyResp._1 = malloc(4096).cast[CString]
    !bodyResp._2 = 0
    val headersResp = malloc(sizeof[CurlFetch]).cast[Ptr[CurlFetch]]
    !headersResp._1 = malloc(4096).cast[CString]
    !headersResp._2 = 0
    val httpCode = malloc(sizeof[Long]).cast[Ptr[Long]]
    new CurlSpaces(bodyResp, headersResp, httpCode)
  }

  private def parseHeaders(str: String): Seq[(String, String)] = {
    val array = str
      .split("\n")
      .filter(_.trim.length > 0)
      .map { line =>
        val split = line.split(":", 2)
        if (split.size == 2)
          split(0).trim -> split(1).trim
        else
          split(0).trim -> ""
      }
    Seq.from(array: _*)
  }

  private def setMethod(handle: CurlHandle, method: Method)(implicit z: Zone): R[CurlCode] = {
    import com.softwaremill.sttp.curl.CurlApi._
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

  private def readResponseBody[T](response: String, responseAs: ResponseAs[T, S]): R[T] = {
    responseAs match {
      case MappedResponseAs(raw, g) => responseMonad.map(readResponseBody(response, raw))(g)
      case IgnoreResponse           => responseMonad.unit((): Unit)
      case ResponseAsString(enc)    => responseMonad.unit(response)
      case ResponseAsByteArray      => toByteArray(response)
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
