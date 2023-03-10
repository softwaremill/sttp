package sttp.client4.examples

import io.circe.generic.auto._
import sttp.client4._
import sttp.client4.circe._
import sttp.client4.logging.slf4j.Slf4jLoggingBackend
import sttp.client4.logging.LogConfig

object LogRequestsSlf4j extends App {
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse].getRight)

  val backend: SyncBackend =
    Slf4jLoggingBackend(
      HttpClientSyncBackend(),
      LogConfig(
        includeTiming = true,
        logRequestBody = false,
        logResponseBody = false
      )
    )

  try {
    val response: Response[HttpBinResponse] = request.send(backend)
    println("Done! " + response.code)
  } finally backend.close()
}
