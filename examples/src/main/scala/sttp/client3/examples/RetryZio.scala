package sttp.client3.examples

import sttp.client3._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.{ExitCode, Schedule, ZIO}
import zio.clock.Clock
import zio.duration._

object RetryZio extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    AsyncHttpClientZioBackend().flatMap { backend =>
      val localhostRequest = basicRequest
        .get(uri"http://localhost/test")
        .response(asStringAlways)

      val sendWithRetries: ZIO[Clock, Throwable, Response[String]] = localhostRequest
        .send(backend)
        .either
        .repeat(
          Schedule.spaced(1.second) *>
            Schedule.recurs(10) *>
            Schedule.recurWhile(result => RetryWhen.Default(localhostRequest, result))
        )
        .absolve

      sendWithRetries.ensuring(backend.close().ignore)
    }.exitCode
  }
}
