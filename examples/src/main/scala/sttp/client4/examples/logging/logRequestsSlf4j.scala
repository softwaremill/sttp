// {cat=Logging; effects=Direct; backend=HttpClient}: Add a logging backend wrapper, which uses slf4j

//> using dep com.softwaremill.sttp.client4::circe:4.0.0-RC1
//> using dep com.softwaremill.sttp.client4::slf4j-backend:4.0.0-RC1
//> using dep io.circe::circe-generic:0.14.12
//> using dep ch.qos.logback:logback-classic:1.5.18

package sttp.client4.examples.logging

import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.logging.LogConfig
import sttp.client4.logging.slf4j.Slf4jLoggingBackend

@main def logRequestsSlf4j(): Unit =
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse].orFail)

  val backend: SyncBackend =
    Slf4jLoggingBackend(
      DefaultSyncBackend(),
      LogConfig(
        logRequestBody = true,
        logResponseBody = true
      )
    )

  try
    val response: Response[HttpBinResponse] = request.send(backend)
    println("Done! " + response.code)
  finally backend.close()
