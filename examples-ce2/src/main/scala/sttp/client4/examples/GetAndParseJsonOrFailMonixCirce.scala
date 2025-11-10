// {cat=JSON; effects=Monix; backend=HttpClient}: Receive & parse JSON using circe

//> using scala 2.13
//> using dep com.softwaremill.sttp.client4::monix:4.0.13
//> using dep com.softwaremill.sttp.client4::circe:4.0.13
//> using dep io.circe::circe-generic:0.14.15

package sttp.client4.examples

import io.circe.generic.auto._
import sttp.client4._
import sttp.client4.httpclient.monix.HttpClientMonixBackend
import sttp.client4.circe._

object GetAndParseJsonOrFailMonixCirce extends App {
  import monix.execution.Scheduler.Implicits.global

  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request: Request[HttpBinResponse] = basicRequest
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse].orFail)

  HttpClientMonixBackend
    .resource()
    .use { backend =>
      request.send(backend).map { response: Response[HttpBinResponse] =>
        println(s"Got response code: ${response.code}")
        println(response.body)
      }
    }
    .runSyncUnsafe()
}
