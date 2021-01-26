package sttp.client3.opentracing

import io.opentracing.tag.Tags
import io.opentracing.{Span, SpanContext, Tracer}
import io.opentracing.propagation.Format
import io.opentracing.Tracer.SpanBuilder
import sttp.capabilities.Effect
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.client3.{FollowRedirectsBackend, Request, Response, SttpBackend}
import sttp.client3.opentracing.OpenTracingBackend._

import scala.collection.JavaConverters._

class OpenTracingBackend[F[_], P] private (delegate: SttpBackend[F, P], tracer: Tracer) extends SttpBackend[F, P] {

  private implicit val _monad: MonadError[F] = responseMonad
  type PE = P with Effect[F]

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    responseMonad
      .eval {
        val spanBuilderTransformer: SpanBuilderTransformer =
          request
            .tag(OpenTracingBackend.SpanBuilderTransformerRequestTag)
            .collectFirst { case f: SpanBuilderTransformer =>
              f
            }
            .getOrElse(identity)
        val span = spanBuilderTransformer(
          tracer
            .buildSpan(
              request
                .tag(OpenTracingBackend.OperationIdRequestTag)
                .getOrElse("default-operation-id")
                .toString
            )
        ).withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
          .withTag(Tags.HTTP_METHOD, request.method.method)
          .withTag(Tags.HTTP_URL, request.uri.toString)
          .withTag(Tags.COMPONENT, "sttp2-client")
          .start()

        request
          .tag(OpenTracingBackend.SpanTransformerRequestTag)
          .collectFirst { case spanTranformer: SpanTransformer => spanTranformer(span) }
          .getOrElse(span)
      }
      .flatMap { span =>
        val requestBuilderAdapter = new RequestBuilderAdapter(request)
        tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderCarrier(requestBuilderAdapter))
        responseMonad.handleError(
          delegate.send(requestBuilderAdapter.request).map { response =>
            span
              .setTag(Tags.HTTP_STATUS, Integer.valueOf(response.code.code))
              .finish()
            response
          }
        ) { case e =>
          span
            .setTag(Tags.ERROR, java.lang.Boolean.TRUE)
            .log(Map("event" -> Tags.ERROR.getKey, "error.object" -> e).asJava)
            .finish()
          responseMonad.error(e)
        }
      }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object OpenTracingBackend {
  private val OperationIdRequestTag = "io.opentracing.tag.sttp.operationId"
  private val SpanBuilderTransformerRequestTag = "io.opentracing.tag.sttp.span.builder.transformer"
  private val SpanTransformerRequestTag = "io.opentracing.tag.sttp.span.transformer"
  type SpanBuilderTransformer = SpanBuilder => SpanBuilder
  type SpanTransformer = Span => Span

  implicit class RichRequest[T, S](request: Request[T, S]) {
    def tagWithOperationId(operationId: String): Request[T, S] =
      request.tag(OperationIdRequestTag, operationId)

    def tagWithTransformSpan(transformSpan: SpanTransformer): Request[T, S] =
      request.tag(SpanTransformerRequestTag, transformSpan)

    /** Sets transformation of SpanBuilder used by OpenTracing backend to create Span this request execution. */
    def tagWithTransformSpanBuilder(transformSpan: SpanBuilderTransformer): Request[T, S] =
      request.tag(SpanBuilderTransformerRequestTag, transformSpan)

    /** Sets parent Span for OpenTracing Span of this request execution. */
    def setOpenTracingParentSpan(parent: Span): Request[T, S] =
      tagWithTransformSpanBuilder(_.asChildOf(parent))

    /** Sets parent SpanContext for OpenTracing Span of this request execution. */
    def setOpenTracingParentSpanContext(parentSpanContext: SpanContext): Request[T, S] =
      tagWithTransformSpanBuilder(_.asChildOf(parentSpanContext))
  }

  def apply[F[_], P](delegate: SttpBackend[F, P], tracer: Tracer): SttpBackend[F, P] = {
    new FollowRedirectsBackend[F, P](new OpenTracingBackend(delegate, tracer))
  }
}
