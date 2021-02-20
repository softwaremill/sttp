package sttp.client3.armeria

import com.linecorp.armeria.client.{
  ClientFactory,
  ClientRequestContext,
  Clients,
  ResponseTimeoutException,
  UnprocessedRequestException,
  WebClient
}
import com.linecorp.armeria.common.{HttpData, HttpMethod, HttpRequest, HttpResponse, RequestHeaders, ResponseHeaders}
import com.linecorp.armeria.common.stream.ClosedStreamException
import io.netty.buffer.Unpooled
import io.netty.util.AsciiString
import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import org.reactivestreams.Publisher
import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}
import sttp.capabilities.{Effect, Streams}
import sttp.client3.SttpClientException.{ConnectException, ReadException}
import sttp.client3.armeria.AbstractArmeriaBackend.{DefaultFileBufferSize, RightUnit, noopAutoCloseable, noopCanceler}
import sttp.client3.{
  ByteArrayBody,
  ByteBufferBody,
  DefaultReadTimeout,
  FileBody,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  Response,
  StreamBody,
  StringBody,
  SttpBackend,
  SttpBackendOptions,
  SttpClientException
}
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
    val releaseToken = customizeOptions(request)
    val captor = Clients.newContextCaptor()
    var success = false
    try {
      val armeriaReq = requestToArmeria(request)
      val armeriaRes = client.execute(armeriaReq)
      Try(captor.get()) match {
        case Failure(ex) =>
          // Failed to start a request
          monad.async[Response[T]] { cb =>
            armeriaReq
              .whenComplete()
              .asInstanceOf[CompletableFuture[Void]]
              .handle((_: Void, cause: Throwable) => {
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
          val response = fromArmeriaResponse(request, armeriaRes, ctx)
          success = true
          response
      }
    } finally {
      if (success) {
        captor.close()
        if (releaseToken ne noopAutoCloseable) {
          releaseToken.close()
        }
      }
    }
  }

  private def requestToArmeria(request: Request[_, Nothing]): HttpRequest = {
    val method = methodToArmeria(request.method)
    val path = request.uri.toString()

    val headers = headersToArmeria(request.headers, method, path)

    request.body match {
      case NoBody => HttpRequest.of(headers)
      case StringBody(s, encoding, _) =>
        val charset =
          if (encoding == "utf-8" || encoding == "UTF-8") {
            StandardCharsets.UTF_8
          } else {
            Charset.forName(encoding)
          }
        HttpRequest.of(headers, HttpData.of(charset, s))
      case FileBody(f, _) =>
        HttpRequest.of(headers, new PathPublisher(f.toPath, DefaultFileBufferSize))
      case ByteArrayBody(b, _) => HttpRequest.of(headers, HttpData.wrap(b))
      case InputStreamBody(is, _) =>
        HttpRequest.of(headers, HttpData.wrap(is.readAllBytes()))
      case ByteBufferBody(b, _) =>
        HttpRequest.of(headers, HttpData.wrap(Unpooled.wrappedBuffer(b)))
      case MultipartBody(_) => throw new IllegalArgumentException("Multipart body is not supported")
      case StreamBody(s)    => HttpRequest.of(headers, streamToPublisher(s.asInstanceOf[streams.BinaryStream]))
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

  private def headersToArmeria(headers: Seq[Header], method: HttpMethod, path: String): RequestHeaders = {
    val builder = RequestHeaders.builder(method, path)
    headers.foreach(h => {
      builder.add(h.name, h.value)
    })
    builder.build()
  }

  private def customizeOptions[T, R >: SE](request: Request[T, R]): AutoCloseable = {
    val readTimeout = request.options.readTimeout
    // If a readTimeout is not configured, respects Armeria's responseTimeout
    if (readTimeout ne DefaultReadTimeout) {
      Clients.withContextCustomizer((ctx: ClientRequestContext) => {
        ctx.setResponseTimeoutMillis(readTimeout.toMillis)
      })
    } else {
      noopAutoCloseable
    }
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

  def fromArmeriaResponse[T, R >: SE](
      request: Request[T, R],
      response: HttpResponse,
      ctx: ClientRequestContext
  ): F[Response[T]] = {
    val splitHttpResponse = response.split()
    val aggregatorRef = new AtomicReference[StreamMessageAggregator]()
    monad
      .async[ResponseHeaders](cb => {
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
      })
      .flatMap { headers =>
        {
          val meta = headersToResponseMeta(headers)
          bodyFromStreamMessage(ctx.eventLoop(), aggregatorRef)(request.response, meta, Left(splitHttpResponse.body()))
            .map(body => {
              Response(
                body,
                StatusCode.unsafeApply(headers.status().code()),
                headers.status.codeAsText(),
                meta.headers,
                Nil,
                request.onlyMetadata
              )
            })
        }
      }
  }

  private def headersToResponseMeta(responseHeaders: ResponseHeaders): ResponseMetadata = {
    val builder = Seq.newBuilder[Header]
    builder.sizeHint(responseHeaders.size())
    responseHeaders.forEach((key: AsciiString, value) => {
      // Skip pseudo header
      if (key.charAt(0) != ':') {
        builder += new Header(key.toString(), value)
      }
    })
    val status = responseHeaders.status()
    ResponseMetadata(StatusCode.unsafeApply(status.code()), status.codeAsText(), builder.result())
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
  val DefaultFileBufferSize: Int = 4096
  val RightUnit: Either[Nothing, Unit] = Right(())
  val noopAutoCloseable: AutoCloseable = () => {}
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
      .decorator(delegate => new HttpDecodingClient(delegate))
      .build()
  }

  def newClient(options: SttpBackendOptions): WebClient = {
    WebClient
      .builder()
      .decorator(delegate => new HttpDecodingClient(delegate))
      .factory(newClientFactory(options))
      .build()
  }
}
