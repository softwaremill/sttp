package sttp.client3.http4s

import java.io.{InputStream, UnsupportedEncodingException}
import java.nio.charset.Charset
import cats.data.NonEmptyList
import cats.effect.{Async, Deferred, Resource}
import cats.implicits._
import cats.effect.implicits._
import fs2.compression.InflateParams
import fs2.io.file.Files
import fs2.{Chunk, Stream}
import org.http4s.{ContentCoding, EntityBody, Request => Http4sRequest}
import org.http4s
import org.http4s.client.Client
import org.http4s.blaze.client.BlazeClientBuilder
import org.typelevel.ci.CIString
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.http4s.Http4sBackend.EncodingHandler
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.internal.{BodyFromResponseAs, IOBufferSize, SttpFile, throwNestedMultipartNotAllowed}
import sttp.model._
import sttp.monad.MonadError
import sttp.client3.testing.SttpBackendStub
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{BasicRequestBody, NoBody, RequestBody, Response, SttpBackend, _}

import scala.concurrent.ExecutionContext

// needs http4s using cats-effect
class Http4sBackend[F[_]: Async](
    client: Client[F],
    customizeRequest: Http4sRequest[F] => Http4sRequest[F],
    customEncodingHandler: EncodingHandler[F]
) extends SttpBackend[F, Fs2Streams[F]] {
  type PE = Fs2Streams[F] with sttp.capabilities.Effect[F]
  override def send[T, R >: PE](r: Request[T, R]): F[Response[T]] =
    adjustExceptions(r) {
      val (entity, extraHeaders) = bodyToHttp4s(r, r.body)
      val request = Http4sRequest(
        method = methodToHttp4s(r.method),
        uri = http4s.Uri.unsafeFromString(r.uri.toString),
        headers =
          http4s.Headers(r.headers.map(h => http4s.Header.Raw(CIString(h.name), h.value)).toList) ++ extraHeaders,
        body = entity.body
      )

      // see adr0001
      Deferred[F, Unit].flatMap { responseBodyCompleteVar =>
        Deferred[F, Either[Throwable, Response[T]]].flatMap { responseVar =>
          val sendRequest = client
            .run(customizeRequest(request))
            .use { response =>
              val code = StatusCode.unsafeApply(response.status.code)
              val headers = response.headers.headers.map(h => Header(h.name.toString, h.value))
              val statusText = response.status.reason
              val responseMetadata = ResponseMetadata(code, statusText, headers)

              val signalBodyComplete = responseBodyCompleteVar.complete(()).map(_ => ())
              val body =
                bodyFromResponseAs(signalBodyComplete)(
                  r.response,
                  responseMetadata,
                  Left(
                    onFinalizeSignal(
                      decompressResponseBodyIfNotHead(r.method, response),
                      signalBodyComplete
                    )
                  )
                )

              body
                .map { b => Response(b, code, statusText, headers, Nil, r.onlyMetadata) }
                .flatMap(r => responseVar.complete(Right(r)))
                .flatMap(_ => responseBodyCompleteVar.get)
            }
            .recoverWith { case t: Throwable => responseVar.complete(Left(t)).as(()) }

          sendRequest.start >> responseVar.get.flatMap {
            case Left(t)  => implicitly[cats.ApplicativeError[F, Throwable]].raiseError(t)
            case Right(r) => r.pure[F]
          }
        }
      }
    }

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
        http4s.EntityEncoder.inputStreamEncoder[F, InputStream].toEntity(b.pure[F])

      case FileBody(b, _) =>
        http4s.EntityEncoder.fileEncoder.toEntity(b.toFile)
    }
  }

  private def bodyToHttp4s[R >: PE](
      r: Request[_, R],
      body: RequestBody[R]
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

  private def multipartToHttp4s(mp: Part[RequestBody[_]]): http4s.multipart.Part[F] = {
    val contentDisposition =
      http4s.Header.Raw(CIString(HeaderNames.ContentDisposition), mp.contentDispositionHeaderValue)
    val otherHeaders = mp.headers.map(h => http4s.Header.Raw(CIString(h.name), h.value))
    val allHeaders = List(contentDisposition) ++ otherHeaders

    val body: EntityBody[F] = mp.body match {
      case NoBody                 => Stream.empty
      case body: BasicRequestBody => basicBodyToHttp4s(body).body
      case StreamBody(b)          => b.asInstanceOf[EntityBody[F]]
      case MultipartBody(_)       => throwNestedMultipartNotAllowed
    }

    http4s.multipart.Part(http4s.Headers(allHeaders), body)
  }

  private def onFinalizeSignal(hr: http4s.Response[F], signal: F[Unit]): http4s.Response[F] = {
    hr.copy(body = hr.body.onFinalize(signal))
  }

  private def decompressResponseBodyIfNotHead[T](m: Method, hr: http4s.Response[F]): http4s.Response[F] = {
    if (m == Method.HEAD) hr else decompressResponseBody(hr)
  }

  private def decompressResponseBody(hr: http4s.Response[F]): http4s.Response[F] = {
    val body = hr.headers
      .get[http4s.headers.`Content-Encoding`]
      .map(e => customEncodingHandler.orElse(EncodingHandler(standardEncodingHandler))(hr.body -> e.contentCoding))
      .getOrElse(hr.body)
    hr.copy(body = body)
  }

  private def standardEncodingHandler: (EntityBody[F], ContentCoding) => EntityBody[F] = {
    case (body, contentCoding)
        if http4s.headers
          .`Accept-Encoding`(NonEmptyList.of(http4s.ContentCoding.deflate))
          .satisfiedBy(contentCoding) =>
      body.through(fs2.compression.Compression[F].inflate(InflateParams.DEFAULT))
    case (body, contentCoding)
        if http4s.headers
          .`Accept-Encoding`(NonEmptyList.of(http4s.ContentCoding.gzip, http4s.ContentCoding.`x-gzip`))
          .satisfiedBy(contentCoding) =>
      body.through(fs2.compression.Compression[F].gunzip(4096)).flatMap(_.content)
    case (_, contentCoding) => throw new UnsupportedEncodingException(s"Unsupported encoding: ${contentCoding.coding}")
  }

  private def bodyFromResponseAs(signalBodyComplete: F[Unit]) =
    new BodyFromResponseAs[F, http4s.Response[F], Nothing, EntityBody[F]] {
      override protected def withReplayableBody(
          response: http4s.Response[F],
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[http4s.Response[F]] = {
        val body = replayableBody match {
          case Left(byteArray) => Stream.chunk(Chunk.array(byteArray))
          case Right(file)     => Files[F].readAll(file.toPath, IOBufferSize)
        }

        response.copy(body = body).pure[F]
      }

      override protected def regularIgnore(response: http4s.Response[F]): F[Unit] = response.body.compile.drain

      override protected def regularAsByteArray(response: http4s.Response[F]): F[Array[Byte]] = response.as[Array[Byte]]

      override protected def regularAsFile(response: http4s.Response[F], file: SttpFile): F[SttpFile] = {
        val f = file.toFile
        if (!f.exists()) {
          f.getParentFile.mkdirs()
          f.createNewFile()
        }

        response.body.through(Files[F].writeAll(file.toPath)).compile.drain.map(_ => file)
      }

      override protected def regularAsStream(response: http4s.Response[F]): F[(EntityBody[F], () => F[Unit])] =
        (response.body, () => signalBodyComplete).pure[F]

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: Nothing
      ): F[T] = ws

      override protected def cleanupWhenNotAWebSocket(
          response: http4s.Response[F],
          e: NotAWebSocketException
      ): F[Unit] = ().pure[F]

      override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): F[Unit] = response
    }

  private def adjustExceptions[T](r: Request[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(http4sExceptionToSttpClientException(r, _))

  private def http4sExceptionToSttpClientException(request: Request[_, _], e: Exception): Option[Exception] =
    e match {
      case e: org.http4s.client.ConnectionFailure => Some(new SttpClientException.ConnectException(request, e))
      case e: org.http4s.InvalidBodyException     => Some(new SttpClientException.ReadException(request, e))
      case e: org.http4s.InvalidResponseException => Some(new SttpClientException.ReadException(request, e))
      case e: Exception                           => SttpClientException.defaultExceptionToSttpClientException(request, e)
    }

  override implicit val responseMonad: MonadError[F] = new CatsMonadAsyncError

  // no-op. Client lifecycle is managed by Resource
  override def close(): F[Unit] = responseMonad.unit(())
}

object Http4sBackend {

  type EncodingHandler[F[_]] = PartialFunction[(EntityBody[F], ContentCoding), EntityBody[F]]
  object EncodingHandler {
    def apply[F[_]](f: (EntityBody[F], ContentCoding) => EntityBody[F]): EncodingHandler[F] = { case (b, c) =>
      f(b, c)
    }
  }

  def usingClient[F[_]: Async](
      client: Client[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      customEncodingHandler: EncodingHandler[F] = PartialFunction.empty
  ): SttpBackend[F, Fs2Streams[F]] =
    new FollowRedirectsBackend[F, Fs2Streams[F]](
      new Http4sBackend[F](client, customizeRequest, customEncodingHandler)
    )

  def usingBlazeClientBuilder[F[_]: Async](
      blazeClientBuilder: BlazeClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      customEncodingHandler: EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F]]] = {
    blazeClientBuilder.resource.map(c => usingClient(c, customizeRequest, customEncodingHandler))
  }

  def usingDefaultBlazeClientBuilder[F[_]: Async](
      clientExecutionContext: ExecutionContext = ExecutionContext.global,
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      customEncodingHandler: EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F]]] =
    usingBlazeClientBuilder(
      BlazeClientBuilder[F](clientExecutionContext),
      customizeRequest,
      customEncodingHandler
    )

  /** Create a stub backend for testing, which uses the `F` response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[sttp.client3.testing.SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: SttpBackendStub[F, Fs2Streams[F]] = SttpBackendStub(new CatsMonadAsyncError)
}
