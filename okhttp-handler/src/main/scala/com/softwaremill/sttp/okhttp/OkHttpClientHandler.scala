package com.softwaremill.sttp.okhttp

import java.io.IOException
import java.nio.charset.Charset

import com.softwaremill.sttp._
import ResponseAs.EagerResponseHandler
import okhttp3.internal.http.HttpMethod
import okhttp3.{
  Call,
  Callback,
  Headers,
  MediaType,
  OkHttpClient,
  MultipartBody => OkHttpMultipartBody,
  Request => OkHttpRequest,
  RequestBody => OkHttpRequestBody,
  Response => OkHttpResponse
}
import okio.{BufferedSink, Okio}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Try}

abstract class OkHttpHandler[R[_], S](client: OkHttpClient)
    extends SttpHandler[R, S] {
  private[okhttp] def convertRequest[T](request: Request[T, S]): OkHttpRequest = {
    val builder = new OkHttpRequest.Builder()
      .url(request.uri.toString)

    val body = bodyToOkHttp(request.body)
    builder.method(request.method.m, body.getOrElse {
      if (HttpMethod.requiresRequestBody(request.method.m))
        OkHttpRequestBody.create(null, "")
      else null
    })

    //OkHttp support automatic gzip compression
    request.headers
      .filter(_._1.equalsIgnoreCase(AcceptEncodingHeader) == false)
      .foreach {
        case (name, value) => builder.addHeader(name, value)
      }

    builder.build()
  }

  private def bodyToOkHttp[T](body: RequestBody[S]): Option[OkHttpRequestBody] = {
    body match {
      case NoBody => None
      case StringBody(b, _, _) =>
        Some(OkHttpRequestBody.create(null, b))
      case ByteArrayBody(b, _) =>
        Some(OkHttpRequestBody.create(null, b))
      case ByteBufferBody(b, _) =>
        Some(OkHttpRequestBody.create(null, b.array()))
      case InputStreamBody(b, _) =>
        Some(new OkHttpRequestBody() {
          override def writeTo(sink: BufferedSink): Unit =
            sink.writeAll(Okio.source(b))
          override def contentType(): MediaType = null
        })
      case PathBody(b, _) =>
        Some(OkHttpRequestBody.create(null, b.toFile))
      case StreamBody(s) =>
        streamToRequestBody(s)
      case MultipartBody(ps) =>
        val b = new OkHttpMultipartBody.Builder()
          .setType(OkHttpMultipartBody.FORM)
        ps.foreach(addMultipart(b, _))
        Some(b.build())
    }
  }

  private def addMultipart(builder: OkHttpMultipartBody.Builder,
                           mp: Multipart): Unit = {
    val allHeaders = mp.additionalHeaders + (ContentDispositionHeader -> mp.contentDispositionHeaderValue)
    val headers = Headers.of(allHeaders.asJava)

    bodyToOkHttp(mp.body).foreach(builder.addPart(headers, _))
  }

  private[okhttp] def readResponse[T](
      res: OkHttpResponse,
      responseAs: ResponseAs[T, S]): R[Response[T]] = {

    val code = res.code()

    val body = if (codeIsSuccess(code)) {
      responseMonad.map(responseHandler(res).handle(responseAs, responseMonad))(
        Right(_))
    } else {
      responseMonad.map(responseHandler(res).handle(asString, responseMonad))(
        Left(_))
    }

    val headers = res
      .headers()
      .names()
      .asScala
      .flatMap(name => res.headers().values(name).asScala.map((name, _)))

    responseMonad.map(body)(Response(_, res.code(), headers.toList, Nil))
  }

  private def responseHandler(res: OkHttpResponse) =
    new EagerResponseHandler[S] {
      override def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T] =
        bra match {
          case IgnoreResponse =>
            Try(res.close())
          case ResponseAsString(encoding) =>
            val body = Try(
              res.body().source().readString(Charset.forName(encoding)))
            res.close()
            body
          case ResponseAsByteArray =>
            val body = Try(res.body().bytes())
            res.close()
            body
          case ras @ ResponseAsStream() =>
            responseBodyToStream(res).map(ras.responseIsStream)
          case ResponseAsFile(file, overwrite) =>
            val body = Try(
              ResponseAs.saveFile(file, res.body().byteStream(), overwrite))
            res.close()
            body
        }
    }

  def streamToRequestBody(stream: S): Option[OkHttpRequestBody] = None

  def responseBodyToStream(res: OkHttpResponse): Try[S] =
    Failure(new IllegalStateException("Streaming isn't supported"))

  override def close(): Unit = {}
}

object OkHttpHandler {
  def buildClientNoRedirects(): OkHttpClient =
    new OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .build()
}

class OkHttpSyncHandler private (client: OkHttpClient)
    extends OkHttpHandler[Id, Nothing](client) {
  override protected def doSend[T](r: Request[T, Nothing]): Response[T] = {
    val request = convertRequest(r)
    val response = client.newCall(request).execute()
    readResponse(response, r.response)
  }

  override def responseMonad: MonadError[Id] = IdMonad
}

object OkHttpSyncHandler {
  def apply(okhttpClient: OkHttpClient = OkHttpHandler.buildClientNoRedirects())
    : SttpHandler[Id, Nothing] =
    new OkHttpSyncHandler(okhttpClient)
}

abstract class OkHttpAsyncHandler[R[_], S](client: OkHttpClient,
                                           rm: MonadAsyncError[R])
    extends OkHttpHandler[R, S](client) {
  override protected def doSend[T](r: Request[T, S]): R[Response[T]] = {
    val request = convertRequest(r)

    rm.flatten(rm.async[R[Response[T]]] { cb =>
      def success(r: R[Response[T]]) = cb(Right(r))
      def error(t: Throwable) = cb(Left(t))

      client
        .newCall(request)
        .enqueue(new Callback {
          override def onFailure(call: Call, e: IOException): Unit =
            error(e)

          override def onResponse(call: Call, response: OkHttpResponse): Unit =
            try success(readResponse(response, r.response))
            catch { case e: Exception => error(e) }
        })
    })
  }

  override def responseMonad: MonadError[R] = rm
}

class OkHttpFutureHandler private (client: OkHttpClient)(
    implicit ec: ExecutionContext)
    extends OkHttpAsyncHandler[Future, Nothing](client, new FutureMonad) {}

object OkHttpFutureHandler {
  def apply(
      okhttpClient: OkHttpClient = OkHttpHandler.buildClientNoRedirects())(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    new OkHttpFutureHandler(okhttpClient)
}
