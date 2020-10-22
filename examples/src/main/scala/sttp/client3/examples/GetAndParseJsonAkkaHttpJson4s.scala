package sttp.client3.examples

object GetAndParseJsonAkkaHttpJson4s extends App {
  import scala.concurrent.Future

  import sttp.client3._
  import sttp.client3.akkahttp._
  import sttp.client3.json4s._

  import scala.concurrent.ExecutionContext.Implicits.global

  case class HttpBinResponse(origin: String, headers: Map[String, String])

  implicit val serialization = org.json4s.native.Serialization
  implicit val formats = org.json4s.DefaultFormats
  val request = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse])

  val backend: SttpBackend[Future, Any] = AkkaHttpBackend()
  val response: Future[Response[Either[ResponseException[String, Exception], HttpBinResponse]]] =
    request.send(backend)

  for {
    r <- response
  } {
    println(s"Got response code: ${r.code}")
    println(r.body)
    backend.close()
  }
}
