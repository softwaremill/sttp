// {cat=Backend wrapper; effects=Future; backend=HttpClient}: Integrate with resilience4j to implement rate-limiting

//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC2
//> using dep io.github.resilience4j:resilience4j-ratelimiter:2.3.0

package sttp.client4.examples.wrapper

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import sttp.capabilities.Effect
import sttp.client4.*
import sttp.client4.httpclient.HttpClientFutureBackend
import sttp.client4.wrappers.DelegateBackend
import sttp.monad.FutureMonad
import sttp.monad.MonadError

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class RateLimitingBackendWrapper[F[_], P](
    rateLimiter: RateLimiter,
    delegate: GenericBackend[F, P]
)(implicit monadError: MonadError[F])
    extends DelegateBackend(delegate):

  override def send[T](request: GenericRequest[T, P & Effect[F]]): F[Response[T]] =
    RateLimitingBackendWrapper.decorateF(rateLimiter, delegate.send(request))

object RateLimitingBackendWrapper:
  def apply[F[_]](
      rateLimiter: RateLimiter,
      backend: Backend[F]
  )(using monadError: MonadError[F]): Backend[F] =
    new RateLimitingBackendWrapper(rateLimiter, backend) with Backend[F] {}

  def decorateF[F[_], T](
      rateLimiter: RateLimiter,
      service: => F[T]
  )(using monadError: MonadError[F]): F[T] =
    import sttp.monad.syntax._
    monadError.blocking(RateLimiter.waitForPermission(rateLimiter)).flatMap(_ => service)

@main def rateLimiterCatsEffect: Unit =
  import scala.concurrent.ExecutionContext.Implicits.global
  val rawBackend = HttpClientFutureBackend()
  val backend = RateLimitingBackendWrapper(
    // 1 request every 3 seconds
    RateLimiter
      .of(
        "sttp",
        RateLimiterConfig.custom().limitRefreshPeriod(java.time.Duration.ofSeconds(3)).limitForPeriod(1).build()
      ),
    rawBackend
  )(using FutureMonad())

  def send =
    println(s"${Instant.now()} sending ...")
    basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asStringOrFail)
      .send(backend)

  def sendMany(counter: Int): Future[Unit] =
    if counter > 0 then send.flatMap(_ => sendMany(counter - 1))
    else Future.unit

  Await.result(sendMany(5), Duration.Inf)
