package sttp.client4.examples

import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.logging.LogConfig
import sttp.client4.logging.slf4j.Slf4jLoggingBackend

@main def logRequestsSlf4j(): Unit =
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse].orFail)

  val backend: SyncBackend =
    Slf4jLoggingBackend(
      HttpClientSyncBackend(),
      LogConfig(
        includeTiming = true,
        logRequestBody = false,
        logResponseBody = false
      )
    )

  try
    val response: Response[HttpBinResponse] = request.send(backend)
    println("Done! " + response.code)
  finally backend.close()
