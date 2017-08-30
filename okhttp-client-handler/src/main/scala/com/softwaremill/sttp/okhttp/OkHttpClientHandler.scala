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

abstract class OkHttpClientHandler[R[_], S](client: OkHttpClient)
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

    val body = responseHandler(res).handle(responseAs, responseMonad)

    val headers = res
      .headers()
      .names()
      .asScala
      .flatMap(name => res.headers().values(name).asScala.map((name, _)))

    responseMonad.map(body, Response(_: T, res.code(), headers.toList))
  }

  private def responseHandler(res: OkHttpResponse) =
    new EagerResponseHandler[S] {
      override def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T] =
        bra match {
          case IgnoreResponse => Try(res.body().close())
          case ResponseAsString(encoding) =>
            Try(res.body().source().readString(Charset.forName(encoding)))
          case ResponseAsByteArray => Try(res.body().bytes())
          case ras @ ResponseAsStream() =>
            responseBodyToStream(res).map(ras.responseIsStream)
          case ResponseAsFile(file, overwrite) =>
            Try(ResponseAs.saveFile(file, res.body().byteStream(), overwrite))
        }
    }

  def streamToRequestBody(stream: S): Option[OkHttpRequestBody] = None

  def responseBodyToStream(res: OkHttpResponse): Try[S] =
    Failure(new IllegalStateException("Streaming isn't supported"))
}

class OkHttpSyncClientHandler private (client: OkHttpClient)
    extends OkHttpClientHandler[Id, Nothing](client) {
  override def send[T](r: Request[T, Nothing]): Response[T] = {
    val request = convertRequest(r)
    val response = client.newCall(request).execute()
    readResponse(response, r.response)
  }

  override def responseMonad: MonadError[Id] = IdMonad
}

object OkHttpSyncClientHandler {
  def apply(okhttpClient: OkHttpClient = new OkHttpClient())
    : SttpHandler[Id, Nothing] =
    new OkHttpSyncClientHandler(okhttpClient)
}

abstract class OkHttpAsyncClientHandler[R[_], S](client: OkHttpClient,
                                                 rm: MonadAsyncError[R])
    extends OkHttpClientHandler[R, S](client) {
  override def send[T](r: Request[T, S]): R[Response[T]] = {
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

class OkHttpFutureClientHandler private (client: OkHttpClient)(
    implicit ec: ExecutionContext)
    extends OkHttpAsyncClientHandler[Future, Nothing](client, new FutureMonad) {}

object OkHttpFutureClientHandler {
  def apply(okhttpClient: OkHttpClient = new OkHttpClient())(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    new OkHttpFutureClientHandler(okhttpClient)
}
