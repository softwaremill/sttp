package sttp.client3

import java.io.ByteArrayInputStream

import sttp.client3.curl.CurlApi._
import sttp.client3.curl.CurlCode.CurlCode
import sttp.client3.curl.CurlInfo._
import sttp.client3.curl.CurlOption.{Header => _, _}
import sttp.client3.curl._
import sttp.client3.internal._
import sttp.client3.ws.{NotAWebSocketException, GotAWebSocketException}
import sttp.capabilities.Effect
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.model.{Header, Method, ResponseMetadata, StatusCode}

import scala.collection.immutable.Seq
import scala.io.Source
import scala.scalanative.unsafe
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.scalanative.unsigned._
import scala.scalanative.unsafe.{CSize, Ptr, _}

abstract class AbstractCurlBackend[F[_]](monad: MonadError[F], verbose: Boolean) extends SttpBackend[F, Any] {
  override implicit val responseMonad: MonadError[F] = monad

  override def close(): F[Unit] = monad.unit(())

  private var headers: CurlList = _
  private var multiPartHeaders: Seq[CurlList] = Seq()

  type PE = Any with Effect[F]

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    unsafe.Zone { implicit z =>
      val curl = CurlApi.init
      if (verbose) {
        curl.option(Verbose, parameter = true)
      }
      if (request.tags.nonEmpty) {
        responseMonad.error(new UnsupportedOperationException("Tags are not supported"))
      }
      val reqHeaders = request.headers
      if (reqHeaders.nonEmpty) {
        reqHeaders.find(_.name == "Accept-Encoding").foreach(h => curl.option(AcceptEncoding, h.value))
        request.body match {
          case _: MultipartBody[_] =>
            headers = transformHeaders(
              reqHeaders :+ Header.contentType(MediaType.MultipartFormData)
            )
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
        val responseBody = fromCString((!spaces.bodyResp)._1)
        val responseHeaders_ = parseHeaders(fromCString((!spaces.headersResp)._1))
        val httpCode = StatusCode((!spaces.httpCode).toInt)

        if (headers.ptr != null) headers.ptr.free()
        multiPartHeaders.foreach(_.ptr.free())
        free((!spaces.bodyResp)._1)
        free((!spaces.headersResp)._1)
        free(spaces.bodyResp.asInstanceOf[Ptr[CSignedChar]])
        free(spaces.headersResp.asInstanceOf[Ptr[CSignedChar]])
        free(spaces.httpCode.asInstanceOf[Ptr[CSignedChar]])
        curl.cleanup()

        val statusText = responseHeaders_.head.name.split(" ").last
        val responseHeaders = responseHeaders_.tail
        val responseMetadata = ResponseMetadata(httpCode, statusText, responseHeaders)

        val body: F[T] = bodyFromResponseAs(request.response, responseMetadata, Left(responseBody))
        responseMonad.map(body) { b =>
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

  private def setMethod(handle: CurlHandle, method: Method)(implicit z: Zone): F[CurlCode] = {
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
      case Method(m)      => handle.option(CustomRequest, m)
    }
    lift(m)
  }

  private def setRequestBody[R >: PE](curl: CurlHandle, body: RequestBody[R])(implicit zone: Zone): F[CurlCode] =
    body match { // todo: assign to responseMonad object
      case b: BasicRequestBody =>
        val str = basicBodyToString(b)
        lift(curl.option(PostFields, toCString(str)))
      case MultipartBody(parts) =>
        val mime = curl.mime
        parts.foreach { case p @ Part(name, partBody, _, headers) =>
          val part = mime.addPart()
          part.withName(name)
          val str = basicBodyToString(partBody)
          part.withData(str)
          p.fileName.foreach(part.withFileName(_))
          p.contentType.foreach(part.withMimeType(_))

          val otherHeaders = headers.filterNot(_.is(HeaderNames.ContentType))
          if (otherHeaders.nonEmpty) {
            val curlList = transformHeaders(otherHeaders)
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

  private def basicBodyToString(body: RequestBody[_]): String =
    body match {
      case StringBody(b, _, _)   => b
      case ByteArrayBody(b, _)   => new String(b)
      case ByteBufferBody(b, _)  => new String(b.array)
      case InputStreamBody(b, _) => Source.fromInputStream(b).mkString
      case FileBody(f, _)        => Source.fromFile(f.toFile).mkString
      case NoBody                => new String("")
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
    Seq(array: _*)
  }

  private lazy val bodyFromResponseAs = new BodyFromResponseAs[F, String, Nothing, Nothing] {
    override protected def withReplayableBody(
        response: String,
        replayableBody: Either[Array[Byte], SttpFile]
    ): F[String] = response.unit

    override protected def regularIgnore(response: String): F[Unit] = ().unit

    override protected def regularAsByteArray(response: String): F[Array[Byte]] = toByteArray(response)

    override protected def regularAsFile(response: String, file: SttpFile): F[SttpFile] =
      responseMonad
        .map(toByteArray(response)) { a =>
          val is = new ByteArrayInputStream(a)
          val f = FileHelpers.saveFile(file.toFile, is)
          SttpFile.fromFile(f)
        }

    override protected def regularAsStream(response: String): F[(Nothing, () => F[Unit])] =
      throw new IllegalStateException("CurlBackend does not support streaming responses")

    override protected def handleWS[T](
        responseAs: WebSocketResponseAs[T, _],
        meta: ResponseMetadata,
        ws: Nothing
    ): F[T] = ws

    override protected def cleanupWhenNotAWebSocket(
        response: String,
        e: NotAWebSocketException
    ): F[Unit] = ().unit

    override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): F[Unit] = response
  }

  private def transformHeaders(reqHeaders: Iterable[Header])(implicit z: Zone): CurlList = {
    reqHeaders
      .map { header => s"${header.name}: ${header.value}" }
      .foldLeft(new CurlList(null)) { case (acc, h) =>
        new CurlList(acc.ptr.append(h))
      }
  }

  private def toByteArray(str: String): F[Array[Byte]] = responseMonad.unit(str.getBytes)

  private def lift(code: CurlCode): F[CurlCode] = {
    code match {
      case CurlCode.Ok => responseMonad.unit(code)
      case _           => responseMonad.error(new RuntimeException(s"Command failed with status $code"))
    }
  }
}

object AbstractCurlBackend {
  val wdFunc: CFuncPtr4[Ptr[Byte], CSize, CSize, Ptr[CurlFetch], CSize] = {
    (ptr: Ptr[CChar], size: CSize, nmemb: CSize, data: Ptr[CurlFetch]) =>
      val index: CSize = (!data)._2
      val increment: CSize = size * nmemb
      (!data)._2 = (!data)._2 + increment
      (!data)._1 = realloc((!data)._1, (!data)._2 + 1.toUInt)
      memcpy((!data)._1 + index, ptr, increment)
      !(!data)._1.+((!data)._2) = 0.toByte
      size * nmemb
  }
}
