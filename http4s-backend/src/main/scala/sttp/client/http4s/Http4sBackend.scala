package sttp.client.http4s

import java.io.{File, InputStream, UnsupportedEncodingException}
import java.nio.charset.Charset

import cats.data.NonEmptyList
import cats.effect.concurrent.MVar
import cats.effect.{ConcurrentEffect, ContextShift, Resource}
import cats.implicits._
import cats.effect.implicits._
import fs2.{Chunk, Stream}
import org.http4s.{Request => Http4sRequest}
import org.http4s
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.model._
import sttp.client.monad.MonadError
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
import scala.language.higherKinds

class Http4sBackend[F[_]: ConcurrentEffect: ContextShift](
    client: Client[F],
    blockingExecutionContext: ExecutionContext,
    customizeRequest: Http4sRequest[F] => Http4sRequest[F]
) extends SttpBackend[F, Stream[F, Byte], NothingT] {

  override def send[T](r: Request[T, Stream[F, Byte]]): F[Response[T]] = {
    val (entity, extraHeaders) = bodyToHttp4s(r, r.body)
    val request = Http4sRequest(
      method = methodToHttp4s(r.method),
      uri = http4s.Uri.unsafeFromString(r.uri.toString),
      headers = http4s.Headers(r.headers.map(h => http4s.Header(h.name, h.value)).toList) ++ extraHeaders,
      body = entity.body
    )

    // see adr0001
    MVar.empty[F, Unit].flatMap { responseBodyCompleteVar =>
      MVar.empty[F, Response[T]].flatMap { responseVar =>
        val sendRequest = client.fetch(customizeRequest(request)) { response =>
          val code = StatusCode.unsafeApply(response.status.code)
          val headers = response.headers.toList.map(h => Header.notValidated(h.name.value, h.value))
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
            .map { b =>
              Response(b, code, statusText, headers, Nil)
            }
            .flatMap(responseVar.put)
            .flatMap(_ => responseBodyCompleteVar.take)
        }

        sendRequest.start >> responseVar.take
      }
    }
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, Stream[F, Byte]],
      handler: NothingT[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = handler // nothing is everything

  private def methodToHttp4s(m: Method): http4s.Method = m match {
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
        http4s.EntityEncoder.inputStreamEncoder[F, InputStream](blockingExecutionContext).toEntity(b.pure[F])

      case FileBody(b, _) =>
        http4s.EntityEncoder.fileEncoder(blockingExecutionContext).toEntity(b.toFile)
    }
  }

  private def bodyToHttp4s(
      r: Request[_, Stream[F, Byte]],
      body: RequestBody[Stream[F, Byte]]
  ): (http4s.Entity[F], http4s.Headers) = {
    body match {
      case NoBody => (http4s.Entity(http4s.EmptyBody: http4s.EntityBody[F]), http4s.Headers.empty)

      case b: BasicRequestBody => (basicBodyToHttp4s(b), http4s.Headers.empty)

      case StreamBody(s) =>
        val cl = r.headers
          .find(_.is(HeaderNames.ContentLength))
          .map(_.value.toLong)
        (http4s.Entity(s, cl), http4s.Headers.empty)

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
    val body = hr.headers.get(http4s.headers.`Content-Encoding`) match {
      case Some(encoding)
          if http4s.headers
            .`Accept-Encoding`(NonEmptyList.of(http4s.ContentCoding.gzip, http4s.ContentCoding.`x-gzip`))
            .satisfiedBy(encoding.contentCoding) =>
        hr.body.through(fs2.compress.gunzip(4096))
      case Some(encoding)
          if http4s.headers
            .`Accept-Encoding`(NonEmptyList.of(http4s.ContentCoding.deflate))
            .satisfiedBy(encoding.contentCoding) =>
        hr.body.through(fs2.compress.inflate())
      case Some(encoding) =>
        throw new UnsupportedEncodingException(s"Unsupported encoding: ${encoding.contentCoding.coding}")
      case None => hr.body
    }

    hr.copy(body = body)
  }

  private def bodyFromHttp4s[T](
      rr: ResponseAs[T, Stream[F, Byte]],
      hr: http4s.Response[F],
      meta: ResponseMetadata
  ): F[T] = {
    def saved(file: File) = {
      if (!file.exists()) {
        file.getParentFile.mkdirs()
        file.createNewFile()
      }

      hr.body.through(fs2.io.file.writeAll(file.toPath, blockingExecutionContext)).compile.drain
    }

    rr match {
      case MappedResponseAs(raw, g) =>
        bodyFromHttp4s(raw, hr, meta).map(g(_, meta))

      case ResponseAsFromMetadata(f) =>
        bodyFromHttp4s(f(meta), hr, meta)

      case IgnoreResponse =>
        hr.body.compile.drain

      case ResponseAsByteArray =>
        hr.as[Array[Byte]]

      case r @ ResponseAsStream() =>
        r.responseIsStream(hr.body).pure[F]

      case ResponseAsFile(file) =>
        saved(file.toFile).map(_ => file)
    }
  }

  override def responseMonad: MonadError[F] = new CatsMonadAsyncError

  // no-op. Client lifecycle is managed by Resource
  override def close(): F[Unit] = responseMonad.unit(())
}

object Http4sBackend {
  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: Client[F],
      blockingExecutionContext: ExecutionContext = ExecutionContext.Implicits.global,
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _
  ): SttpBackend[F, Stream[F, Byte], NothingT] =
    new FollowRedirectsBackend[F, Stream[F, Byte], NothingT](
      new Http4sBackend[F](client, blockingExecutionContext, customizeRequest)
    )

  def usingClientBuilder[F[_]: ConcurrentEffect: ContextShift](
      blazeClientBuilder: BlazeClientBuilder[F],
      blockingExecutionContext: ExecutionContext = ExecutionContext.Implicits.global,
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _
  ): Resource[F, SttpBackend[F, Stream[F, Byte], NothingT]] = {
    blazeClientBuilder.resource.map(c => usingClient(c, blockingExecutionContext, customizeRequest))
  }

  def usingDefaultClientBuilder[F[_]: ConcurrentEffect: ContextShift](
      clientExecutionContext: ExecutionContext = ExecutionContext.Implicits.global,
      blockingExecutionContext: ExecutionContext = ExecutionContext.Implicits.global,
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _
  ): Resource[F, SttpBackend[F, Stream[F, Byte], NothingT]] =
    usingClientBuilder(BlazeClientBuilder[F](clientExecutionContext), blockingExecutionContext, customizeRequest)
}
