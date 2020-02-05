package sttp.client.examples

import sttp.client._
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.{Schedule, ZIO}
import zio.clock.Clock
import zio.duration._

object RetryZio extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    AsyncHttpClientZioBackend()
      .flatMap { implicit backend =>
        val localhostRequest = basicRequest
          .get(uri"http://localhost/test")
          .response(asStringAlways)

        val sendWithRetries: ZIO[Clock, Throwable, Response[String]] = localhostRequest
          .send()
          .either
          .repeat(
            Schedule.spaced(1.second) *>
              Schedule.recurs(10) *>
              Schedule.doWhile(result => RetryWhen.Default(localhostRequest, result))
          )
          .absolve

        sendWithRetries.ensuring(backend.close().catchAll(_ => ZIO.unit))
      }
      .fold(_ => 1, _ => 0)
  }
}
