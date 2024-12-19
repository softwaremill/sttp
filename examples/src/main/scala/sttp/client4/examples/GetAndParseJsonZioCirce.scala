package sttp.client4.examples

import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.httpclient.zio.send
import zio.*

object GetAndParseJsonZioCirce extends ZIOAppDefault:

  override def run = {
    case class HttpBinResponse(origin: String, headers: Map[String, String])

    val request = basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asJson[HttpBinResponse])

    for
      response <- send(request)
      _ <- Console.printLine(s"Got response code: ${response.code}")
      _ <- Console.printLine(response.body.toString)
    yield ()
  }.provideLayer(HttpClientZioBackend.layer())
