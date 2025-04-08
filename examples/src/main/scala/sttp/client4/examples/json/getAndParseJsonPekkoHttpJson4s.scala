// {cat=JSON; effects=Future; backend=Pekko}: Receive & parse JSON using json4s

//> using dep com.softwaremill.sttp.client4::json4s:4.0.0
//> using dep com.softwaremill.sttp.client4::pekko-http-backend:4.0.0
//> using dep org.json4s::json4s-native:4.0.7
//> using dep org.apache.pekko::pekko-stream:1.1.2

package sttp.client4.examples.json

import org.json4s.Formats
import org.json4s.Serialization
import sttp.client4.*
import sttp.client4.json4s.*
import sttp.client4.pekkohttp.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@main def getAndParseJsonPekkoHttpJson4s(): Unit =
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  given Serialization = org.json4s.native.Serialization
  given Formats = org.json4s.DefaultFormats

  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse])

  val backend: Backend[Future] = PekkoHttpBackend()
  val response: Future[Response[Either[ResponseException[String], HttpBinResponse]]] =
    request.send(backend)

  for r <- response do
    println(s"Got response code: ${r.code}")
    println(r.body)
    backend.close()
