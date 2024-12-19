package sttp.client4.examples

import io.circe
import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.httpclient.HttpClientSyncBackend

@main def getRawResponseBodySynchronous(): Unit =
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asBoth(asJson[HttpBinResponse], asStringAlways))

  val backend: SyncBackend = HttpClientSyncBackend()

  try
    val response: Response[(Either[ResponseException[String, circe.Error], HttpBinResponse], String)] =
      request.send(backend)

    val (parsed, raw) = response.body

    println("Got response - parsed: " + parsed)
    println("Got response - raw: " + raw)
  finally backend.close()
