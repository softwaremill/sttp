package sttp.client.examples

import io.circe
import io.circe.generic.auto._
import sttp.client._
import sttp.client.circe._

object GetRawRequestBodySynchronous extends App {
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asBoth(asJson[HttpBinResponse], asStringAlways))

  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  try {
    val response: Response[(Either[ResponseException[String, circe.Error], HttpBinResponse], String)] =
      request.send(backend)

    val (parsed, raw) = response.body

    println("Got response - parsed: " + parsed)
    println("Got response - raw: " + raw)

  } finally backend.close()
}
