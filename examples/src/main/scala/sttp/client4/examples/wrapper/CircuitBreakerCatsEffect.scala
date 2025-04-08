// {cat=Backend wrapper; effects=cats-effect; backend=HttpClient}: Integrate with resilience4j to implement circuit-breaking

//> using dep com.softwaremill.sttp.client4::cats:4.0.0
//> using dep io.github.resilience4j:resilience4j-circuitbreaker:2.3.0
//> using dep org.typelevel::cats-effect:3.5.7

package sttp.client4.examples.wrapper

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import sttp.capabilities.Effect
import sttp.client4.*
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.wrappers.DelegateBackend
import sttp.monad.MonadError

import java.util.concurrent.TimeUnit

class CircuitBreakerBackendWrapper[F[_], P](circuitBreaker: CircuitBreaker, delegate: GenericBackend[F, P])
    extends DelegateBackend(delegate):

  override def send[T](request: GenericRequest[T, P & Effect[F]]): F[Response[T]] =
    CircuitBreakerBackendWrapper.decorateF(circuitBreaker, delegate.send(request))

object CircuitBreakerBackendWrapper:

  def apply[F[_]](circuitBreaker: CircuitBreaker, backend: Backend[F]): Backend[F] =
    new CircuitBreakerBackendWrapper(circuitBreaker, backend) with Backend[F] {}

  private def decorateF[F[_], T](
      circuitBreaker: CircuitBreaker,
      service: => F[T]
  )(using monadError: MonadError[F]): F[T] =
    monadError.suspend:
      if !circuitBreaker.tryAcquirePermission() then
        monadError.error(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))
      else
        val start = System.nanoTime()
        try
          monadError.handleError(monadError.map(service): r =>
            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS)
            r) { case t =>
            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, t)
            monadError.error(t)
          }
        catch
          case t: Throwable =>
            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, t)
            monadError.error(t)

object CircuitBreakerCatsEffect extends IOApp:
  override def run(args: List[String]): IO[ExitCode] = HttpClientCatsBackend
    .resource[IO]()
    .use: rawBackend =>
      val backend = CircuitBreakerBackendWrapper(
        CircuitBreaker.of("sttp", CircuitBreakerConfig.custom().minimumNumberOfCalls(3).build()),
        rawBackend
      )

      def sendFailed = basicRequest
        .get(uri"https://httpbin.org/status/500")
        // if we get a non-2xx response, the result will be a failed IO with an exception
        .response(asStringOrFail)
        .send(backend)
        .recoverWith(e => IO.println(s"Failed with: $e"))

      def sendSuccess = basicRequest
        .get(uri"https://httpbin.org/status/200")
        .response(asStringOrFail)
        .send(backend)
        .flatMap(_ => IO.println("Success"))
        .recoverWith(e => IO.println(s"Failed with: $e"))

      // 1st request - success - goes through
      // 2nd, 3rd request - failures - cause the circuit breaker to close
      // 4th request - failure - is not permitted
      // 5th request - failure - is also not permitted
      (sendSuccess *> sendFailed *> sendFailed *> sendFailed *> sendSuccess).as(ExitCode.Success)
