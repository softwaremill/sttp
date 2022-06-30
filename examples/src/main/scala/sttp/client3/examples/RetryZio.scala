package sttp.client3.examples

import sttp.client3._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.Clock.ClockLive
import zio.internal.stacktracer.Tracer
import zio.{Clock, Schedule, Tag, ZIO, ZIOAppDefault, ZLayer, durationInt}

object RetryZio extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Response[String]] = {
    AsyncHttpClientZioBackend()
      .flatMap { backend =>
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
      }
      .provide(ZLayer.succeed[Clock](ClockLive)(Tag[Clock], Tracer.newTrace))
  }
}
