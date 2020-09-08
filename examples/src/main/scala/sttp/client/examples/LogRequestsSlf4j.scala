package sttp.client.examples

import io.circe.generic.auto._
import sttp.client._
import sttp.client.circe._
import sttp.client.logging.slf4j.Slf4jLoggingBackend

object LogRequestsSlf4j extends App {
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse].failLeft)

  val backend: SttpBackend[Identity, Any] =
    Slf4jLoggingBackend(
      HttpURLConnectionBackend(),
      includeTiming = true,
      logRequestBody = false,
      logResponseBody = false
    )

  try {
    val response: Response[HttpBinResponse] = request.send(backend)
    println("Done! " + response.code)
  } finally backend.close()
}
