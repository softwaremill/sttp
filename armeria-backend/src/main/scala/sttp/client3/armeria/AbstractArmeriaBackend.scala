package sttp.client3.armeria

import com.linecorp.armeria.client.encoding.DecodingClient
import com.linecorp.armeria.client.{
  ClientFactory,
  ClientRequestContext,
  Clients,
  ResponseTimeoutException,
  UnprocessedRequestException,
  WebClient,
  WebClientRequestPreparation
}
import com.linecorp.armeria.common.multipart.{BodyPart, Multipart}
import com.linecorp.armeria.common.stream.{ClosedStreamException, StreamMessage}
import com.linecorp.armeria.common.{
  CommonPools,
  ContentDisposition,
  HttpData,
  HttpHeaders,
  HttpMethod,
  HttpResponse,
  HttpStatus,
  ResponseHeaders,
  MediaType => ArmeriaMediaType
}
import io.netty.buffer.{ByteBufAllocator, Unpooled}
import io.netty.util.AsciiString
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import org.reactivestreams.Publisher
import scala.collection.immutable.Seq
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import sttp.capabilities.{Effect, Streams}
import sttp.client3.SttpClientException.{ConnectException, ReadException}
import sttp.client3._
import sttp.client3.armeria.AbstractArmeriaBackend.{RightUnit, noopCanceler}
import sttp.client3.internal.{throwNestedMultipartNotAllowed, toByteArray}
import sttp.model._
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError, MonadError}

abstract class AbstractArmeriaBackend[F[_], S <: Streams[S]](
    client: WebClient = WebClient.of(),
    closeFactory: Boolean,
    private implicit val monad: MonadAsyncError[F]
) extends SttpBackend[F, S] {

  val streams: Streams[S]

  type SE = S with Effect[F]

  protected def bodyFromStreamMessage: BodyFromStreamMessage[F, S]

  protected def streamToPublisher(stream: streams.BinaryStream): Publisher[HttpData]

  override def responseMonad: MonadError[F] = monad

  override def send[T, R >: SE](request: Request[T, R]): F[Response[T]] =
    adjustExceptions(request)(execute(request))

  private def execute[T, R >: SE](request: Request[T, R]): F[Response[T]] = {
    val captor = Clients.newContextCaptor()
    try {
      val armeriaRes = requestToArmeria(request).execute()
      Try(captor.get()) match {
        case Failure(ex) =>
          // Failed to start a request
          monad.async[Response[T]] { cb =>
            armeriaRes
              .aggregate()
              .asInstanceOf[CompletableFuture[Void]]
              .handle((_: Void, cause: Throwable) => {
                // Get an actual error from a response
                if (cause != null) {
                  cb(Left(cause))
                } else {
                  cb(Left(ex))
                }
                null
              })
            noopCanceler
          }
        case Success(ctx) =>
          fromArmeriaResponse(request, armeriaRes, ctx)
      }
    } catch {
      case NonFatal(ex) => monad.error(ex)
    } finally {
      captor.close()
    }
  }

  private def requestToArmeria(request: Request[_, Nothing]): WebClientRequestPreparation = {
    val requestPreparation = client
      .prepare()
      .disablePathParams()
      .method(methodToArmeria(request.method))
      .path(request.uri.toString())
      .responseTimeoutMillis(request.options.readTimeout.toMillis)

    var customContentType: Option[ArmeriaMediaType] = None
    request.headers.foreach { header =>
      if (header.name.equalsIgnoreCase(HeaderNames.ContentType)) {
        // A Content-Type will be set with the body content
        customContentType = Some(ArmeriaMediaType.parse(header.value))
      } else {
        requestPreparation.header(header.name, header.value)
      }
    }

    val contentType = customContentType.getOrElse(ArmeriaMediaType.parse(request.body.defaultContentType.toString()))

    request.body match {
      case NoBody => requestPreparation
      case StringBody(s, encoding, _) =>
        val charset =
          if (encoding == "utf-8" || encoding == "UTF-8") {
            StandardCharsets.UTF_8
          } else {
            Charset.forName(encoding)
          }
        requestPreparation.content(contentType, HttpData.of(charset, s))
      case FileBody(f, _) =>
        requestPreparation.content(contentType, StreamMessage.of(f.toPath))
      case ByteArrayBody(b, _) =>
        requestPreparation.content(contentType, HttpData.wrap(b))
      case InputStreamBody(is, _) =>
        requestPreparation.content(contentType, HttpData.wrap(toByteArray(is)))
      case ByteBufferBody(b, _) =>
        requestPreparation.content(contentType, HttpData.wrap(Unpooled.wrappedBuffer(b)))
      case multipart @ MultipartBody(_) =>
        val armeriaMultipart = Multipart.of(multipart.parts.map(toArmeriaBodyPart): _*)
        requestPreparation.content(
          contentType.withParameter("boundary", armeriaMultipart.boundary()),
          armeriaMultipart.toStreamMessage
        )
      case StreamBody(s) =>
        requestPreparation.content(contentType, streamToPublisher(s.asInstanceOf[streams.BinaryStream]))
    }
  }

  private def methodToArmeria(method: Method): HttpMethod =
    method match {
      case Method.GET     => HttpMethod.GET
      case Method.HEAD    => HttpMethod.HEAD
      case Method.POST    => HttpMethod.POST
      case Method.PUT     => HttpMethod.PUT
      case Method.DELETE  => HttpMethod.DELETE
      case Method.OPTIONS => HttpMethod.OPTIONS
      case Method.PATCH   => HttpMethod.PATCH
      case Method.CONNECT => HttpMethod.CONNECT
      case Method.TRACE   => HttpMethod.TRACE
      case _              => HttpMethod.UNKNOWN
    }

  private def toArmeriaBodyPart(bodyPart: Part[RequestBody[_]]): BodyPart = {
    val dispositionBuilder = ContentDisposition.builder("form-data")
    dispositionBuilder.name(bodyPart.name)
    bodyPart.fileName.foreach(dispositionBuilder.filename)

    val headersBuilder = HttpHeaders
      .builder()
      .contentDisposition(dispositionBuilder.build())

    bodyPart.headers.foreach { header =>
      headersBuilder.add(header.name, header.value)
    }

    val bodyPartBuilder = BodyPart
      .builder()
      .headers(headersBuilder.build())

    (bodyPart.body match {
      case StringBody(b, encoding, _) =>
        bodyPartBuilder.content(HttpData.wrap(b.getBytes(encoding)))
      case ByteArrayBody(b, _) =>
        bodyPartBuilder.content(HttpData.wrap(b))
      case ByteBufferBody(b, _) =>
        bodyPartBuilder.content(HttpData.wrap(Unpooled.wrappedBuffer(b)))
      case InputStreamBody(is, _) =>
        bodyPartBuilder.content(HttpData.wrap(toByteArray(is)))
      case FileBody(f, _) =>
        bodyPartBuilder.content(StreamMessage.of(f.toPath))
      case StreamBody(s) =>
        bodyPartBuilder.content(streamToPublisher(s.asInstanceOf[streams.BinaryStream]))
      case MultipartBody(_) => throwNestedMultipartNotAllowed
      case NoBody           => bodyPartBuilder.content(HttpData.empty())
    }).build()
  }

  private def adjustExceptions[T](request: Request[_, _])(execute: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(execute) {
      case ex: UnprocessedRequestException =>
        // The cause of an UnprocessedRequestException is always not null
        Some(new ConnectException(request, ex.getCause.asInstanceOf[Exception]))
      case ex @ (_: ClosedStreamException | _: ResponseTimeoutException) =>
        Some(new ReadException(request, ex))
      case ex =>
        SttpClientException.defaultExceptionToSttpClientException(request, ex)
    }

  private def fromArmeriaResponse[T, R >: SE](
      request: Request[T, R],
      response: HttpResponse,
      ctx: ClientRequestContext
  ): F[Response[T]] = {
    val splitHttpResponse = response.split()
    val aggregatorRef = new AtomicReference[StreamMessageAggregator]()
    for {
      headers <- monad.async[ResponseHeaders] { cb =>
        splitHttpResponse
          .headers()
          .handle((headers: ResponseHeaders, cause: Throwable) => {
            if (cause != null) {
              cb(Left(cause))
            } else {
              cb(Right(headers))
            }
            null
          })
        Canceler(() => response.abort())
      }
      meta <- headersToResponseMeta(headers, ctx)
      body <- bodyFromStreamMessage(ctx.eventLoop(), aggregatorRef)(
        request.response,
        meta,
        Left(splitHttpResponse.body())
      )
    } yield Response(
      body,
      meta.code,
      meta.statusText,
      meta.headers,
      Nil,
      request.onlyMetadata
    )
  }

  private def headersToResponseMeta(
      responseHeaders: ResponseHeaders,
      ctx: ClientRequestContext
  ): F[ResponseMetadata] = {
    val status = responseHeaders.status()
    if (status == HttpStatus.UNKNOWN) {
      monad.error(new UnknownStatusException(s"Unknown status. ctx: $ctx"))
    } else {
      val builder = Seq.newBuilder[Header]
      builder.sizeHint(responseHeaders.size())
      responseHeaders.forEach((key: AsciiString, value) => {
        // Skip pseudo header
        if (key.charAt(0) != ':') {
          builder += new Header(key.toString(), value)
        }
      })
      monad.unit(ResponseMetadata(StatusCode.unsafeApply(status.code()), status.codeAsText(), builder.result()))
    }
  }

  override def close(): F[Unit] = {
    if (closeFactory) {
      monad.async(cb => {
        client
          .options()
          .factory()
          .closeAsync()
          .asInstanceOf[CompletableFuture[Void]]
          .handle((_: Void, cause: Throwable) => {
            if (cause != null) {
              cb(Left(cause))
            } else {
              cb(RightUnit)
            }
            null
          })
        noopCanceler
      })
    } else {
      monad.unit(())
    }
  }
}

private[armeria] object AbstractArmeriaBackend {
  val DefaultFileBufferSize: Int = 8192
  val RightUnit: Either[Nothing, Unit] = Right(())
  val noopCanceler: Canceler = Canceler(() => ())

  private def newClientFactory(options: SttpBackendOptions): ClientFactory = {
    val builder = ClientFactory
      .builder()
      .connectTimeoutMillis(options.connectionTimeout.toMillis)
    options.proxy.fold(builder.build()) { proxy =>
      builder
        .proxyConfig(proxy.asJavaProxySelector)
        .build()
    }
  }

  def newClient(): WebClient = {
    WebClient
      .builder()
      .decorator(
        DecodingClient
          .builder()
          .autoFillAcceptEncoding(false)
          .strictContentEncoding(true)
          .newDecorator()
      )
      .build()
  }

  def newClient(options: SttpBackendOptions): WebClient = {
    WebClient
      .builder()
      .decorator(
        DecodingClient
          .builder()
          .autoFillAcceptEncoding(false)
          .strictContentEncoding(true)
          .newDecorator()
      )
      .factory(newClientFactory(options))
      .build()
  }
}
