// {cat=Other; effects=Direct; backend=HttpClient}: Handle the body by both parsing it to JSON and returning the raw string

//> using dep com.softwaremill.sttp.client4::circe:4.0.13
//> using dep io.circe::circe-generic:0.14.15

package sttp.client4.examples.other

import io.circe
import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*

@main def getRawResponseBodySynchronous(): Unit =
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asBoth(asJson[HttpBinResponse], asStringAlways))

  val backend: SyncBackend = DefaultSyncBackend()

  try
    val response: Response[(Either[ResponseException[String], HttpBinResponse], String)] =
      request.send(backend)

    val (parsed, raw) = response.body

    println("Got response - parsed: " + parsed)
    println("Got response - raw: " + raw)
  finally backend.close()
