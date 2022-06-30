package sttp.client3.examples

import io.circe.generic.auto._
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.client3.circe._
import zio.{Console, _}

object GetAndParseJsonZioCirce extends ZIOAppDefault {

  override def run: ZIO[Any, Throwable, Unit] = {

    case class HttpBinResponse(origin: String, headers: Map[String, String])

    val request = basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asJson[HttpBinResponse])

    // create a description of a program, which requires SttpClient dependency in the environment
    val sendAndPrint: ZIO[SttpClient, Throwable, Unit] = for {
      response <- send(request)
      _ <- Console.printLine(s"Got response code: ${response.code}")
      _ <- Console.printLine(response.body.toString)
    } yield ()

    // provide an implementation for the SttpClient dependency
    sendAndPrint
      .provide(AsyncHttpClientZioBackend.layer())

  }
}
