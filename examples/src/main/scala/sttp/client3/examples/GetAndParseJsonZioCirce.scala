package sttp.client3.examples

import sttp.client3._
import sttp.client3.circe._
import sttp.client3.asynchttpclient.zio._
import io.circe.generic.auto._
import zio._
import zio.Console

object GetAndParseJsonZioCirce extends ZIOAppDefault {

  override def run = {

    case class HttpBinResponse(origin: String, headers: Map[String, String])

    val request = basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asJson[HttpBinResponse])

    // create a description of a program, which requires two dependencies in the environment:
    // the SttpClient, and the Console
    val sendAndPrint: ZIO[SttpClient, Throwable, Unit] = for {
      response <- send(request)
      _ <- Console.printLine(s"Got response code: ${response.code}")
      _ <- Console.printLine(response.body.toString)
    } yield ()

    // provide an implementation for the SttpClient dependency; other dependencies are
    // provided by Zio
    sendAndPrint
      .provide(AsyncHttpClientZioBackend.layer())
      .exitCode
  }
}
