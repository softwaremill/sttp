package sttp.client3.examples

import io.circe.generic.auto._
import sttp.client3._
import sttp.client3.circe._
import sttp.client3.httpclient.zio.{HttpClientZioBackend, send}
import zio._

object GetAndParseJsonZioCirce extends ZIOAppDefault {

  override def run = {
    case class HttpBinResponse(origin: String, headers: Map[String, String])

    val request = basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asJson[HttpBinResponse])

    for {
      response <- send(request)
      _ <- Console.printLine(s"Got response code: ${response.code}")
      _ <- Console.printLine(response.body.toString)
    } yield ()
  }.provideLayer(HttpClientZioBackend.layer())
}
