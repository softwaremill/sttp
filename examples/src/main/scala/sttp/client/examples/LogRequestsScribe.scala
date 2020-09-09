package sttp.client.examples

import io.circe.generic.auto._
import sttp.client._
import sttp.client.circe._
import sttp.client.logging.scribe.ScribeLoggingBackend

object LogRequestsScribe extends App {
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse].failLeft)

  val backend: SttpBackend[Identity, Any] =
    ScribeLoggingBackend(
      HttpURLConnectionBackend()
    )

  try {
    val response: Response[HttpBinResponse] = request.send(backend)
    println("Done! " + response.code)
  } finally backend.close()
}
