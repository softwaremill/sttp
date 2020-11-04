package sttp.client3.examples

//import io.circe.generic.auto._
//import sttp.client3._
//import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend
//import sttp.client3.circe._
//
//object GetAndParseJsonGetRightMonixCirce extends App {
//  import monix.execution.Scheduler.Implicits.global
//
//  case class HttpBinResponse(origin: String, headers: Map[String, String])
//
//  val request: Request[HttpBinResponse, Any] = basicRequest
//    .get(uri"https://httpbin.org/get")
//    .response(asJson[HttpBinResponse].getRight)
//
//  AsyncHttpClientMonixBackend
//    .resource()
//    .use { backend =>
//      request.send(backend).map { response: Response[HttpBinResponse] =>
//        println(s"Got response code: ${response.code}")
//        println(response.body)
//      }
//    }
//    .runSyncUnsafe()
//}
