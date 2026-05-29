package sttp.client4.opentelemetry.otel4s

import cats.effect.MonadCancelThrow
import cats.effect.Outcome
import cats.effect.syntax.monadCancel._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.typelevel.otel4s.trace.{SpanKind, StatusCode, Tracer, TracerProvider}
import sttp.capabilities
import sttp.client4._
import sttp.client4.wrappers.{DelegateBackend, FollowRedirectsBackend}

private class Otel4sTracingBackend[F[_]: MonadCancelThrow: Tracer, P](
    delegate: GenericBackend[F, P],
    config: Otel4sTracingConfig
) extends DelegateBackend[F, P](delegate)
    with Backend[F] {

  def send[T](request: GenericRequest[T, P with capabilities.Effect[F]]): F[Response[T]] =
    MonadCancelThrow[F].uncancelable { poll =>
      Tracer[F]
        .spanBuilder(config.spanName(request))
        .withSpanKind(SpanKind.Client)
        .addAttributes(config.requestAttributes(request))
        .build
        .use { span =>
          for {
            headers <- Tracer[F].propagate(Map.empty[String, String])
            response <- poll(delegate.send(request.headers(headers))).guaranteeCase {
              case Outcome.Succeeded(fa) =>
                fa.flatMap { resp =>
                  for {
                    _ <- span.addAttributes(config.responseAttributes(resp))
                    _ <- span.setStatus(StatusCode.Error).unlessA(resp.code.isSuccess)
                  } yield ()
                }

              case Outcome.Errored(e) =>
                span.addAttributes(config.errorAttributes(e))

              case Outcome.Canceled() =>
                MonadCancelThrow[F].unit
            }
          } yield response
        }
    }

}

object Otel4sTracingBackend {

  def apply[F[_]: MonadCancelThrow: TracerProvider](
      delegate: Backend[F],
      config: Otel4sTracingConfig
  ): F[Backend[F]] =
    usingTracer { implicit tracer: Tracer[F] =>
      FollowRedirectsBackend(new Otel4sTracingBackend(delegate, config) with Backend[F])
    }

  def apply[F[_]: MonadCancelThrow: TracerProvider](
      delegate: WebSocketBackend[F],
      config: Otel4sTracingConfig
  ): F[WebSocketBackend[F]] =
    usingTracer { implicit tracer: Tracer[F] =>
      FollowRedirectsBackend(new Otel4sTracingBackend(delegate, config) with WebSocketBackend[F])
    }

  def apply[F[_]: MonadCancelThrow: TracerProvider, S](
      delegate: StreamBackend[F, S],
      config: Otel4sTracingConfig
  ): F[StreamBackend[F, S]] =
    usingTracer { implicit tracer: Tracer[F] =>
      FollowRedirectsBackend(new Otel4sTracingBackend(delegate, config) with StreamBackend[F, S])
    }

  def apply[F[_]: MonadCancelThrow: TracerProvider, S](
      delegate: WebSocketStreamBackend[F, S],
      config: Otel4sTracingConfig
  ): F[WebSocketStreamBackend[F, S]] =
    usingTracer { implicit tracer: Tracer[F] =>
      FollowRedirectsBackend(new Otel4sTracingBackend(delegate, config) with WebSocketStreamBackend[F, S])
    }

  private def usingTracer[F[_]: MonadCancelThrow: TracerProvider, A](f: Tracer[F] => A): F[A] =
    for {
      tracer <- TracerProvider[F].tracer("sttp-client4").withVersion("1.0.0").get
    } yield f(tracer)

}
