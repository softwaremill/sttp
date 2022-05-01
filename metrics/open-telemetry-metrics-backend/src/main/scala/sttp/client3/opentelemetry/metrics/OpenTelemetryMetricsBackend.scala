package sttp.client3.opentelemetry.metrics

import io.opentelemetry.api.OpenTelemetry
import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.MonadError

private class OpenTelemetryMetricsBackend[F[_], P](
    delegate: SttpBackend[F, P],
    openTelemetry: OpenTelemetry,
    spanName: Request[_, _] => String
) extends SttpBackend[F, P] {

  private implicit val _monad: MonadError[F] = responseMonad
  type PE = P with Effect[F]

  def send[T, R >: PE](request: Request[T, R]): F[Response[T]] = ???

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad

}

object OpenTelemetryMetricsBackend {
  def apply[F[_], P](
      delegate: SttpBackend[F, P],
      openTelemetry: OpenTelemetry,
      spanName: Request[_, _] => String = request => s"HTTP ${request.method.method}"
  ): SttpBackend[F, P] =
    new OpenTelemetryMetricsBackend[F, P](delegate, openTelemetry, spanName)
}
