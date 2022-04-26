package sttp.client3.examples

import io.circe
import io.circe.generic.auto._
import sttp.client3._
import sttp.client3.circe._

object GetRawResponseBodySynchronous extends App {
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asBoth(asJson[HttpBinResponse], asStringAlways))

  val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

  try {
    val response: Response[(Either[ResponseException[String, circe.Error], HttpBinResponse], String)] =
      request.send(backend)

    val (parsed, raw) = response.body

    println("Got response - parsed: " + parsed)
    println("Got response - raw: " + raw)

  } finally backend.close()
}
