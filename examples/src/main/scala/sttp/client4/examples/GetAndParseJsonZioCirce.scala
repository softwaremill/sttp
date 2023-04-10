package sttp.client4.examples

import io.circe.generic.auto._
import sttp.client4._
import sttp.client4.circe._
import sttp.client4.httpclient.zio.{send, HttpClientZioBackend}
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
