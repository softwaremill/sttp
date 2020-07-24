package sttp.client.http4s

import java.io.{File, InputStream, UnsupportedEncodingException}
import java.nio.charset.Charset

import cats.data.NonEmptyList
import cats.effect.concurrent.MVar
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Resource}
import cats.implicits._
import cats.effect.implicits._
import fs2.{Chunk, Stream}
import org.http4s.{ContentCoding, EntityBody, Request => Http4sRequest}
import org.http4s
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.http4s.Http4sBackend.EncodingHandler
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.impl.fs2.Fs2Streams
import sttp.model._
import sttp.client.monad.MonadError
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocketResponse
import sttp.client.{
  BasicRequestBody,
  IgnoreResponse,
  NoBody,
  RequestBody,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  SttpBackend,
  _
}

import scala.concurrent.ExecutionContext

class Http4sBackend[F[_]: ConcurrentEffect: ContextShift](
    client: Client[F],
    blocker: Blocker,
    customizeRequest: Http4sRequest[F] => Http4sRequest[F],
    customEncodingHandler: EncodingHandler[F]
) extends SttpBackend[F, Fs2Streams[F], NothingT] {
  override def send[T, R >: Fs2Streams[F]](r: Request[T, R]): F[Response[T]] =
    adjustExceptions {
      val (entity, extraHeaders) = bodyToHttp4s(r, r.body)
      val request = Http4sRequest(
        method = methodToHttp4s(r.method),
        uri = http4s.Uri.unsafeFromString(r.uri.toString),
        headers = http4s.Headers(r.headers.map(h => http4s.Header(h.name, h.value)).toList) ++ extraHeaders,
        body = entity.body
      )

      // see adr0001
      MVar.empty[F, Unit].flatMap { responseBodyCompleteVar =>
        MVar.empty[F, Either[Throwable, Response[T]]].flatMap { responseVar =>
          val sendRequest = client
            .run(customizeRequest(request))
            .use { response =>
              val code = StatusCode.unsafeApply(response.status.code)
              val headers = response.headers.toList.map(h => Header(h.name.value, h.value))
              val statusText = response.status.reason
              val responseMetadata = ResponseMetadata(headers, code, statusText)

              val body =
                bodyFromHttp4s(
                  r.response,
                  signalResponseBodyComplete(
                    decompressResponseBodyIfNotHead(r.method, response),
                    responseBodyCompleteVar.put(())
                  ),
                  responseMetadata
                )

              body
                .map { b => Response(b, code, statusText, headers, Nil) }
                .flatMap(r => responseVar.put(Right(r)))
                .flatMap(_ => responseBodyCompleteVar.take)
            }
            .recoverWith { case t: Throwable => responseVar.put(Left(t)) }

          sendRequest.start >> responseVar.take.flatMap {
            case Left(t)  => implicitly[cats.ApplicativeError[F, Throwable]].raiseError(t)
            case Right(r) => r.pure[F]
          }
        }
      }
    }

  override def openWebsocket[T, WS_RESULT, R >: Fs2Streams[F]](
      request: Request[T, R],
      handler: NothingT[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = handler // nothing is everything

  private def methodToHttp4s(m: Method): http4s.Method =
    m match {
      case Method.GET     => http4s.Method.GET
      case Method.HEAD    => http4s.Method.HEAD
      case Method.POST    => http4s.Method.POST
      case Method.PUT     => http4s.Method.PUT
      case Method.DELETE  => http4s.Method.DELETE
      case Method.OPTIONS => http4s.Method.OPTIONS
      case Method.PATCH   => http4s.Method.PATCH
      case Method.CONNECT => http4s.Method.CONNECT
      case Method.TRACE   => http4s.Method.TRACE
      case _              => http4s.Method.fromString(m.method).right.get
    }

  private def charsetToHttp4s(encoding: String) = http4s.Charset.fromNioCharset(Charset.forName(encoding))

  private def basicBodyToHttp4s(body: BasicRequestBody): http4s.Entity[F] = {
    body match {
      case StringBody(b, encoding, _) =>
        http4s.EntityEncoder.stringEncoder(charsetToHttp4s(encoding)).toEntity(b)

      case ByteArrayBody(b, _) =>
        http4s.EntityEncoder.byteArrayEncoder.toEntity(b)

      case ByteBufferBody(b, _) =>
        http4s.EntityEncoder.chunkEncoder[F].contramap(Chunk.byteBuffer).toEntity(b)

      case InputStreamBody(b, _) =>
        http4s.EntityEncoder.inputStreamEncoder[F, InputStream](blocker).toEntity(b.pure[F])

      case FileBody(b, _) =>
        http4s.EntityEncoder.fileEncoder(blocker).toEntity(b.toFile)
    }
  }

  private def bodyToHttp4s(
      r: Request[_, Fs2Streams[F]],
      body: RequestBody[Fs2Streams[F]]
  ): (http4s.Entity[F], http4s.Headers) = {
    body match {
      case NoBody => (http4s.Entity(http4s.EmptyBody: http4s.EntityBody[F]), http4s.Headers.empty)

      case b: BasicRequestBody => (basicBodyToHttp4s(b), http4s.Headers.empty)

      case StreamBody(s) =>
        val cl = r.headers
          .find(_.is(HeaderNames.ContentLength))
          .map(_.value.toLong)
        (http4s.Entity(s.asInstanceOf[Stream[F, Byte]], cl), http4s.Headers.empty)

      case MultipartBody(ps) =>
        val parts = ps.toVector.map(multipartToHttp4s)
        val multipart = http4s.multipart.Multipart(parts)
        (http4s.EntityEncoder.multipartEncoder.toEntity(multipart), multipart.headers)
    }
  }

  private def multipartToHttp4s(mp: Part[BasicRequestBody]): http4s.multipart.Part[F] = {
    val contentDisposition = http4s.Header(HeaderNames.ContentDisposition, mp.contentDispositionHeaderValue)
    val otherHeaders = mp.headers.map(h => http4s.Header(h.name, h.value))
    val allHeaders = List(contentDisposition) ++ otherHeaders

    http4s.multipart.Part(http4s.Headers(allHeaders), basicBodyToHttp4s(mp.body).body)
  }

  private def signalResponseBodyComplete(hr: http4s.Response[F], signal: F[Unit]): http4s.Response[F] = {
    hr.copy(body = hr.body.onFinalize(signal))
  }

  private def decompressResponseBodyIfNotHead[T](m: Method, hr: http4s.Response[F]): http4s.Response[F] = {
    if (m == Method.HEAD) hr else decompressResponseBody(hr)
  }

  private def decompressResponseBody(hr: http4s.Response[F]): http4s.Response[F] = {
    val body = hr.headers
      .get(http4s.headers.`Content-Encoding`)
      .map(e => customEncodingHandler.orElse(EncodingHandler(standardEncodingHandler))(hr.body -> e.contentCoding))
      .getOrElse(hr.body)
    hr.copy(body = body)
  }

  private def standardEncodingHandler: (EntityBody[F], ContentCoding) => EntityBody[F] = {
    case (body, contentCoding)
        if http4s.headers
          .`Accept-Encoding`(NonEmptyList.of(http4s.ContentCoding.deflate))
          .satisfiedBy(contentCoding) =>
      body.through(fs2.compression.inflate())
    case (body, contentCoding)
        if http4s.headers
          .`Accept-Encoding`(NonEmptyList.of(http4s.ContentCoding.gzip, http4s.ContentCoding.`x-gzip`))
          .satisfiedBy(contentCoding) =>
      body.through(fs2.compression.gunzip(4096)).flatMap(_.content)
    case (_, contentCoding) => throw new UnsupportedEncodingException(s"Unsupported encoding: ${contentCoding.coding}")
  }

  private def bodyFromHttp4s[T](
      rr: ResponseAs[T, Fs2Streams[F]],
      hr: http4s.Response[F],
      meta: ResponseMetadata
  ): F[T] = {
    def saved(file: File) = {
      if (!file.exists()) {
        file.getParentFile.mkdirs()
        file.createNewFile()
      }

      hr.body.through(fs2.io.file.writeAll(file.toPath, blocker)).compile.drain
    }

    rr match {
      case MappedResponseAs(raw, g) =>
        bodyFromHttp4s(raw, hr, meta).map(g(_, meta))

      case ResponseAsFromMetadata(f) =>
        bodyFromHttp4s(f(meta), hr, meta)

      case IgnoreResponse =>
        hr.body.compile.drain.map(_ => ()) // adjusting type because ResponseAs is covariant

      case ResponseAsByteArray =>
        hr.as[Array[Byte]].map(b => b) // adjusting type because ResponseAs is covariant

      case _: ResponseAsStreamUnsafe[_, _] =>
        hr.body.asInstanceOf[T].pure[F]

      case ResponseAsFile(file) =>
        saved(file.toFile).map(_ => file)
    }
  }

  private def adjustExceptions[T](t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(http4sExceptionToSttpClientException)

  private def http4sExceptionToSttpClientException(e: Exception): Option[Exception] =
    e match {
      case e: org.http4s.client.ConnectionFailure => Some(new SttpClientException.ConnectException(e))
      case e: org.http4s.InvalidBodyException     => Some(new SttpClientException.ReadException(e))
      case e: org.http4s.InvalidResponseException => Some(new SttpClientException.ReadException(e))
      case e: Exception                           => SttpClientException.defaultExceptionToSttpClientException(e)
    }

  override def responseMonad: MonadError[F] = new CatsMonadAsyncError

  // no-op. Client lifecycle is managed by Resource
  override def close(): F[Unit] = responseMonad.unit(())
}

object Http4sBackend {

  type EncodingHandler[F[_]] = PartialFunction[(EntityBody[F], ContentCoding), EntityBody[F]]
  object EncodingHandler {
    def apply[F[_]](f: (EntityBody[F], ContentCoding) => EntityBody[F]): EncodingHandler[F] = {
      case (b, c) => f(b, c)
    }
  }

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: Client[F],
      blocker: Blocker,
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      customEncodingHandler: EncodingHandler[F] = PartialFunction.empty
  ): SttpBackend[F, Fs2Streams[F], NothingT] =
    new FollowRedirectsBackend[F, Fs2Streams[F], NothingT](
      new Http4sBackend[F](client, blocker, customizeRequest, customEncodingHandler)
    )

  def usingClientBuilder[F[_]: ConcurrentEffect: ContextShift](
      blazeClientBuilder: BlazeClientBuilder[F],
      blocker: Blocker,
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      customEncodingHandler: EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F], NothingT]] = {
    blazeClientBuilder.resource.map(c => usingClient(c, blocker, customizeRequest, customEncodingHandler))
  }

  def usingDefaultClientBuilder[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      clientExecutionContext: ExecutionContext = ExecutionContext.global,
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      customEncodingHandler: EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F], NothingT]] =
    usingClientBuilder(BlazeClientBuilder[F](clientExecutionContext), blocker, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the `F` response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: SttpBackendStub[F, Fs2Streams[F], NothingT] = SttpBackendStub(new CatsMonadAsyncError)
}
