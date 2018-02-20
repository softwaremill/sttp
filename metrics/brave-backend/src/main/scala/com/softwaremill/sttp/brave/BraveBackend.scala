package com.softwaremill.sttp.brave

import brave.http.{HttpClientAdapter, HttpClientHandler, HttpTracing}
import brave.propagation.TraceContext
import brave.{Span, Tracing}
import com.softwaremill.sttp.brave.BraveBackend._
import com.softwaremill.sttp.{FollowRedirectsBackend, MonadError, Request, Response, SttpBackend}
import zipkin2.Endpoint

import scala.language.higherKinds

class BraveBackend[R[_], S] private (delegate: SttpBackend[R, S],
                                     httpTracing: HttpTracing)
    extends SttpBackend[R, S] {

  // .asInstanceOf as the create method lacks generics in its return type
  private val handler = HttpClientHandler
    .create(httpTracing, SttpHttpClientAdapter)
    .asInstanceOf[HttpClientHandler[AnyRequest, AnyResponse]]

  private val tracer = httpTracing.tracing().tracer()

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    val span = createSpan(request)
    val tracedRequest = injectTracing(span, request)
    val startedSpan =
      handler.handleSend(NoopInjector, tracedRequest, tracedRequest, span)

    sendAndHandleReceive(startedSpan, tracedRequest)
  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad

  private def createSpan(request: AnyRequest): Span = {
    request
      .tag(TraceContextRequestTag)
      .map(_.asInstanceOf[TraceContext]) match {
      case None               => handler.nextSpan(request)
      case Some(traceContext) => tracer.newChild(traceContext)
    }
  }

  private def sendAndHandleReceive[T](
      span: Span,
      request: Request[T, S]): R[Response[T]] = {
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

  private def injectTracing[T](span: Span,
                               request: Request[T, S]): Request[T, S] = {
    /*
    Sadly the Brave API supports only mutable request representations, hence we need to work our way around
    this and inject headers into the traced request with the help of a mutable variable. Later a no-op injector
    is used (during the call to `handleSend`).
     */

    var tracedRequest: Request[T, S] = request

    httpTracing
      .tracing()
      .propagation()
      .injector((_: Request[_, _], key: String, value: String) => {
        tracedRequest = tracedRequest.header(key, value)
      })
      .inject(span.context(), request)

    tracedRequest
  }
}

object BraveBackend {
  private val NoopInjector = new TraceContext.Injector[Request[_, _]] {
    override def inject(traceContext: TraceContext,
                        carrier: Request[_, _]): Unit = {}
  }

  private val TraceContextRequestTag = classOf[TraceContext].getName

  implicit class RichRequest[T, S](request: Request[T, S]) {
    def tagWithTraceContext(traceContext: TraceContext): Request[T, S] =
      request.tag(TraceContextRequestTag, traceContext)
  }

  type AnyRequest = Request[_, _]
  type AnyResponse = Response[_]

  def apply[R[_], S](delegate: SttpBackend[R, S],
                     tracing: Tracing): SttpBackend[R, S] = {
    apply(delegate, HttpTracing.create(tracing))
  }

  def apply[R[_], S](delegate: SttpBackend[R, S],
                     httpTracing: HttpTracing): SttpBackend[R, S] = {
    // redirects should be handled before brave tracing, hence adding the follow-redirects backend on top
    new FollowRedirectsBackend(new BraveBackend(delegate, httpTracing))
  }
}

object SttpHttpClientAdapter
    extends HttpClientAdapter[AnyRequest, AnyResponse] {

  override def method(request: AnyRequest): String = request.method.m

  override def url(request: AnyRequest): String = request.uri.toString

  override def requestHeader(request: AnyRequest, name: String): String =
    request.headers.find(_._1.equalsIgnoreCase(name)).map(_._2).orNull

  override def statusCode(response: AnyResponse): Integer = response.code

  override def parseServerAddress(req: AnyRequest,
                                  builder: Endpoint.Builder): Boolean = {

    if (builder.parseIp(req.uri.host)) {
      req.uri.port.foreach(builder.port(_))
      true
    } else {
      false
    }
  }
}
