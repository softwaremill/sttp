package sttp.client4.opentelemetry.otel4s

import java.util.concurrent.TimeUnit

import cats.Monad
import cats.effect.{Async, Clock, Resource}
import cats.effect.std.Dispatcher
import cats.effect.syntax.resource._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.foldable._
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.{BucketBoundaries, Histogram, MeterProvider, UpDownCounter}
import org.typelevel.otel4s.semconv.attributes.{
  ErrorAttributes,
  HttpAttributes,
  NetworkAttributes,
  ServerAttributes,
  UrlAttributes
}
import sttp.client4.listener.{ListenerBackend, RequestListener}
import sttp.client4._
import sttp.model.{HttpVersion, ResponseMetadata, StatusCode}
import sttp.client4.wrappers.FollowRedirectsBackend

import scala.concurrent.duration.FiniteDuration
import scala.util.chaining._

object Otel4sMetricsBackend {

  def apply[F[_]: Async: MeterProvider](
      delegate: Backend[F],
      config: Otel4sMetricsConfig
  ): Resource[F, Backend[F]] =
    for {
      listener <- metricsListener(config)
    } yield FollowRedirectsBackend(ListenerBackend(delegate, listener))

  def apply[F[_]: Async: MeterProvider](
      delegate: WebSocketBackend[F],
      config: Otel4sMetricsConfig
  ): Resource[F, WebSocketBackend[F]] =
    for {
      listener <- metricsListener(config)
    } yield FollowRedirectsBackend(ListenerBackend(delegate, listener))

  def apply[F[_]: Async: MeterProvider, S](
      delegate: StreamBackend[F, S],
      config: Otel4sMetricsConfig
  ): Resource[F, StreamBackend[F, S]] =
    for {
      listener <- metricsListener(config)
    } yield FollowRedirectsBackend(ListenerBackend(delegate, listener))

  def apply[F[_]: Async: MeterProvider, S](
      delegate: WebSocketStreamBackend[F, S],
      config: Otel4sMetricsConfig
  ): Resource[F, WebSocketStreamBackend[F, S]] =
    for {
      listener <- metricsListener(config)
    } yield FollowRedirectsBackend(ListenerBackend(delegate, listener))

  private def metricsListener[F[_]: Async: MeterProvider, P](
      config: Otel4sMetricsConfig
  ): Resource[F, MetricsRequestListener[F]] =
    for {
      meter <- MeterProvider[F].meter("sttp-client4").withVersion("1.0.0").get.toResource

      requestDuration <- meter
        .histogram[Double]("http.client.request.duration")
        .withExplicitBucketBoundaries(config.requestDurationHistogramBuckets)
        .withDescription("Duration of HTTP client requests.")
        .withUnit("s")
        .create
        .toResource

      requestBodySize <- meter
        .histogram[Long]("http.client.request.body.size")
        .pipe(b => config.requestBodySizeHistogramBuckets.fold(b)(b.withExplicitBucketBoundaries))
        .withDescription("Size of HTTP client request bodies.")
        .withUnit("By")
        .create
        .toResource

      responseBodySize <- meter
        .histogram[Long]("http.client.response.body.size")
        .pipe(b => config.responseBodySizeHistogramBuckets.fold(b)(b.withExplicitBucketBoundaries))
        .withDescription("Size of HTTP client response bodies.")
        .withUnit("By")
        .create
        .toResource

      activeRequests <- meter
        .upDownCounter[Long]("http.client.active_requests")
        .withDescription("Number of active HTTP requests.")
        .withUnit("{request}")
        .create
        .toResource

      dispatcher <- Dispatcher.parallel[F]
    } yield new MetricsRequestListener[F](
      requestDuration,
      requestBodySize,
      responseBodySize,
      activeRequests,
      dispatcher
    )

  private final case class State(start: FiniteDuration, activeRequestsAttributes: Attributes)

  private final class MetricsRequestListener[F[_]: Monad: Clock](
      requestDuration: Histogram[F, Double],
      requestBodySize: Histogram[F, Long],
      responseBodySize: Histogram[F, Long],
      activeRequests: UpDownCounter[F, Long],
      dispatcher: Dispatcher[F]
  ) extends RequestListener[F, State] {
    def before(request: GenericRequest[_, _]): F[State] =
      for {
        start <- Clock[F].realTime
        attributes <- Monad[F].pure(activeRequestAttributes(request))
        _ <- activeRequests.inc(attributes)
      } yield State(start, attributes)

    def responseBodyReceived(request: GenericRequest[_, _], response: ResponseMetadata, state: State): Unit =
      dispatcher.unsafeRunAndForget(captureResponseMetrics(request, response, state))

    def responseHandled(
        request: GenericRequest[_, _],
        response: ResponseMetadata,
        state: State,
        exception: Option[ResponseException[_]]
    ): F[Unit] = {
      // responseBodyReceived is not called for WebSocket requests
      // ignoring the tag as there's no point in capturing timing information for WebSockets
      Monad[F].whenA(request.isWebSocket)(captureResponseMetrics(request, response, state))
    }

    def exception(
        request: GenericRequest[_, _],
        state: State,
        e: Throwable,
        responseBodyReceivedCalled: Boolean
    ): F[Unit] =
      Monad[F].unlessA(responseBodyReceivedCalled) {
        for {
          now <- Clock[F].realTime
          attributes <- Monad[F].pure(fullAttributes(request, None, Some(e.getClass.getName)))
          _ <- requestDuration.record((now - state.start).toUnit(TimeUnit.SECONDS), attributes)
          _ <- request.contentLength.traverse_(size => requestBodySize.record(size, attributes))
          _ <- activeRequests.dec(state.activeRequestsAttributes)
        } yield ()
      }

    private def captureResponseMetrics(
        request: GenericRequest[_, _],
        response: ResponseMetadata,
        state: State
    ): F[Unit] =
      for {
        now <- Clock[F].realTime
        attributes <- Monad[F].pure(fullAttributes(request, response))
        _ <- requestDuration.record((now - state.start).toUnit(TimeUnit.SECONDS), attributes)
        _ <- request.contentLength.traverse_(length => requestBodySize.record(length, attributes))
        _ <- response.contentLength.traverse_(length => responseBodySize.record(length, attributes))
        _ <- activeRequests.dec(state.activeRequestsAttributes)
      } yield ()

    private def activeRequestAttributes(request: GenericRequest[_, _]): Attributes = {
      val b = Attributes.newBuilder

      b += HttpAttributes.HttpRequestMethod(request.method.method)
      b ++= ServerAttributes.ServerAddress.maybe(request.uri.host)
      b ++= ServerAttributes.ServerPort.maybe(request.uri.port.map(_.toLong))
      b ++= UrlAttributes.UrlScheme.maybe(request.uri.scheme)

      b.result()
    }

    private def fullAttributes(request: GenericRequest[_, _], response: ResponseMetadata): Attributes =
      fullAttributes(
        request,
        Some(response.code),
        Option.unless(response.isSuccess)(response.code.toString())
      )

    private def fullAttributes(
        request: GenericRequest[_, _],
        responseStatusCode: Option[StatusCode],
        errorType: Option[String]
    ): Attributes = {
      val b = Attributes.newBuilder

      b += HttpAttributes.HttpRequestMethod(request.method.method)
      b ++= ServerAttributes.ServerAddress.maybe(request.uri.host)
      b ++= ServerAttributes.ServerPort.maybe(request.uri.port.map(_.toLong))
      b ++= NetworkAttributes.NetworkProtocolVersion.maybe(request.httpVersion.map(networkProtocol))
      b ++= UrlAttributes.UrlScheme.maybe(request.uri.scheme)

      // response
      b ++= HttpAttributes.HttpResponseStatusCode.maybe(responseStatusCode.map(_.code.toLong))
      b ++= ErrorAttributes.ErrorType.maybe(errorType)

      b.result()
    }

    private def networkProtocol(httpVersion: HttpVersion): String =
      httpVersion match {
        case HttpVersion.HTTP_1   => "1.0"
        case HttpVersion.HTTP_1_1 => "1.1"
        case HttpVersion.HTTP_2   => "2"
        case HttpVersion.HTTP_3   => "3"
      }
  }

}
