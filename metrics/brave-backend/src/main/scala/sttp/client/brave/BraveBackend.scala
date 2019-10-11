package sttp.client.brave

import brave.http.{HttpClientAdapter, HttpClientHandler, HttpTracing}
import brave.propagation.{Propagation, TraceContext}
import brave.{Span, Tracing}
import sttp.client.brave.BraveBackend._
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{FollowRedirectsBackend, NothingT, Request, Response, SttpBackend}

import scala.language.higherKinds

class BraveBackend[F[_], S] private (delegate: SttpBackend[F, S, NothingT], httpTracing: HttpTracing)
    extends SttpBackend[F, S, NothingT] {

  // .asInstanceOf as the create method lacks generics in its return type
  private val handler = HttpClientHandler
    .create(httpTracing, SttpHttpClientAdapter)
    .asInstanceOf[HttpClientHandler[AnyRequest, AnyResponse]]

  private val tracer = httpTracing.tracing().tracer()

  override def send[T](request: Request[T, S]): F[Response[T]] = {
    val span = createSpan(request)
    val tracedRequest = injectTracing(span, request)
    val startedSpan =
      handler.handleSend(NoopInjector, tracedRequest, tracedRequest, span)

    sendAndHandleReceive(startedSpan, tracedRequest)
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: NothingT[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = handler // nothing is everything

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad

  private def createSpan(request: AnyRequest): Span = {
    request
      .tag(TraceContextRequestTag)
      .map(_.asInstanceOf[TraceContext]) match {
      case None               => handler.nextSpan(request)
      case Some(traceContext) => tracer.newChild(traceContext)
    }
  }

  private def sendAndHandleReceive[T](span: Span, request: Request[T, S]): F[Response[T]] = {
    val spanInScope = tracer.withSpanInScope(span)

    responseMonad.handleError(
      responseMonad.map(delegate.send(request)) { response =>
        spanInScope.close()
        handler.handleReceive(response, null, span)
        response
      }
    ) {
      case e: Exception =>
        spanInScope.close()
        handler.handleReceive(null, e, span)
        responseMonad.error(e)
    }
  }

  private def injectTracing[T](span: Span, request: Request[T, S]): Request[T, S] = {
    /*
    Sadly the Brave API supports only mutable request representations, hence we need to work our way around
    this and inject headers into the traced request with the help of a mutable variable. Later a no-op injector
    is used (during the call to `handleSend`).
     */

    var tracedRequest: Request[T, S] = request

    httpTracing
      .tracing()
      .propagation()
      .injector(new Propagation.Setter[AnyRequest, String] {
        override def put(carrier: AnyRequest, key: String, value: String): Unit = {
          tracedRequest = tracedRequest.header(key, value)
        }
      })
      .inject(span.context(), request)

    tracedRequest
  }
}

object BraveBackend {
  private val NoopInjector = new TraceContext.Injector[Request[_, _]] {
    override def inject(traceContext: TraceContext, carrier: Request[_, _]): Unit = {}
  }

  private val TraceContextRequestTag = classOf[TraceContext].getName

  implicit class RichRequest[T, S](request: Request[T, S]) {
    def tagWithTraceContext(traceContext: TraceContext): Request[T, S] =
      request.tag(TraceContextRequestTag, traceContext)
  }

  type AnyRequest = Request[_, _]
  type AnyResponse = Response[_]

  def apply[F[_], S](delegate: SttpBackend[F, S, NothingT], tracing: Tracing): SttpBackend[F, S, NothingT] = {
    apply(delegate, HttpTracing.create(tracing))
  }

  def apply[F[_], S](delegate: SttpBackend[F, S, NothingT], httpTracing: HttpTracing): SttpBackend[F, S, NothingT] = {
    // redirects should be handled before brave tracing, hence adding the follow-redirects backend on top
    new FollowRedirectsBackend[F, S, NothingT](new BraveBackend(delegate, httpTracing))
  }
}

object SttpHttpClientAdapter extends HttpClientAdapter[AnyRequest, AnyResponse] {

  override def method(request: AnyRequest): String = request.method.method

  override def url(request: AnyRequest): String = request.uri.toString

  override def requestHeader(request: AnyRequest, name: String): String =
    request.headers.find(_.name.equalsIgnoreCase(name)).map(_.value).orNull

  override def statusCode(response: AnyResponse): Integer = response.code.code
}
