package sttp.client3.examples

import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Schedule, Task, ZIO, ZIOAppDefault, durationInt}

object RetryZio extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Response[String]] = {
    HttpClientZioBackend()
      .flatMap { backend =>
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
      }
  }
}
