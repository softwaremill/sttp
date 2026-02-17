// {cat=Resilience; effects=Direct; backend=HttpClient}: Retry sending a request using Ox

//> using dep com.softwaremill.sttp.client4::ox:4.0.18

package sttp.client4.examples.resilience

import ox.Ox
import ox.OxApp
import ox.resilience.ResultPolicy
import ox.resilience.RetryConfig
import ox.resilience.retry
import ox.scheduling.Schedule
import sttp.client4.*

import scala.concurrent.duration.*

object RetryOx extends OxApp.Simple:
  override def run(using Ox): Unit =
    val backend = DefaultSyncBackend()

    val retryWhen = RetryWhen.Default
    def resultPolicy[T](request: Request[T]) = ResultPolicy[Throwable, Response[T]](
      isSuccess = response => !retryWhen(request, Right(response)),
      isWorthRetrying = e => retryWhen(request, Left(e))
    )
    def sendRequestWithRetries[T](request: Request[T]): Response[T] =
      retry(
        RetryConfig(
          Schedule.fixedInterval(1.second).maxRepeats(3),
          resultPolicy = resultPolicy(request),
          afterAttempt = (attempt, _) => println(s"Retrying ($attempt) ...")
        )
      )(request.send(backend))

    println("Sending request with retries ...")
    println(sendRequestWithRetries(basicRequest.get(uri"https://httpbin.org/status/500")))
