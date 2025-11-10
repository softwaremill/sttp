// {cat=Resilience; effects=ZIO; backend=HttpClient}: Retry sending a request using ZIO's retries

//> using dep com.softwaremill.sttp.client4::zio:4.0.13

package sttp.client4.examples.resilience

import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.Schedule
import zio.Task
import zio.ZIO
import zio.ZIOAppDefault
import zio.durationInt

object RetryZio extends ZIOAppDefault:
  override def run: ZIO[Any, Throwable, Response[String]] =
    HttpClientZioBackend()
      .flatMap: backend =>
        val localhostRequest = basicRequest
          .get(uri"http://localhost/test")
          .response(asStringAlways)

        val sendWithRetries: Task[Response[String]] = localhostRequest
          .send(backend)
          .either
          .repeat(
            Schedule.spaced(1.second) *>
              Schedule.recurs(10) *>
              Schedule.recurWhile(result => RetryWhen.Default(localhostRequest, result))
          )
          .absolve

        sendWithRetries.ensuring(backend.close().ignore)
