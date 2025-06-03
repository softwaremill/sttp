// {cat=JSON; effects=ZIO; backend=HttpClient}: Receive & parse JSON using ZIO Json

//> using dep com.softwaremill.sttp.client4::zio:4.0.8
//> using dep com.softwaremill.sttp.client4::zio-json:4.0.8

package sttp.client4.examples.json

import sttp.client4.*
import sttp.client4.ziojson.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.httpclient.zio.send
import zio.*
import zio.json.JsonDecoder
import zio.json.DeriveJsonDecoder

object GetAndParseJsonZioJson extends ZIOAppDefault:

  case class HttpBinResponse(origin: String, headers: Map[String, String])

  object HttpBinResponse:
    given JsonDecoder[HttpBinResponse] = DeriveJsonDecoder.gen[HttpBinResponse]

  override def run = {
    val request = basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asJson[HttpBinResponse])

    for
      response <- send(request)
      _ <- Console.printLine(s"Got response code: ${response.code}")
      _ <- Console.printLine(response.body.toString)
    yield ()
  }.provideLayer(HttpClientZioBackend.layer())
