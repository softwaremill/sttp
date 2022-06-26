package sttp.client3.examples

import sttp.client3._
import sttp.client3.circe._
import sttp.client3.asynchttpclient.zio._
import io.circe.generic.auto._
import zio.Clock.ClockLive
import zio._
import zio.Console
import zio.Console.ConsoleLive
import zio.internal.stacktracer.Tracer

object GetAndParseJsonZioCirce extends ZIOAppDefault {

  override def run: ZIO[Any, Throwable, Unit] = {

    case class HttpBinResponse(origin: String, headers: Map[String, String])

    val request = basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asJson[HttpBinResponse])

    // create a description of a program, which requires two dependencies in the environment:
    // the SttpClient, and the Console
    val sendAndPrint: ZIO[Console with SttpClient, Throwable, Unit] = for {
      response <- send(request)
      _ <- Console.printLine(s"Got response code: ${response.code}")
      _ <- Console.printLine(response.body.toString)
    } yield ()

    // provide an implementation for the SttpClient and Console dependencies
    sendAndPrint
      .provide(AsyncHttpClientZioBackend.layer(), ZLayer.succeed[Console](ConsoleLive)(Tag[Console], Tracer.newTrace))

  }
}
