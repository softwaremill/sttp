// {cat=JSON; effects=ZIO; backend=HttpClient}: Receive & parse JSON using circe

//> using dep com.softwaremill.sttp.client4::circe:4.0.0-M20
//> using dep com.softwaremill.sttp.client4::zio:4.0.0-M20
//> using dep io.circe::circe-generic:0.14.10

package sttp.client4.examples.json

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
