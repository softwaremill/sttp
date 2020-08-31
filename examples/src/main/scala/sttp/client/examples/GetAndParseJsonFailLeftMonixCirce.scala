package sttp.client.examples

import io.circe.generic.auto._
import sttp.client._
import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend
import sttp.client.circe._

object GetAndParseJsonFailLeftMonixCirce extends App {
  import monix.execution.Scheduler.Implicits.global

  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request: Request[HttpBinResponse, Any] = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse].failLeft)

  AsyncHttpClientMonixBackend
    .resource()
    .use { backend =>
      request.send(backend).map { response: Response[HttpBinResponse] =>
        println(s"Got response code: ${response.code}")
        println(response.body)
      }
    }
    .runSyncUnsafe()
}
