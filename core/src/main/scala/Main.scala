import sttp.client3.Request
import sttp.client3._
import sttp.model.{HeaderNames, MediaType}

object Main extends App{
  //  val backend = HttpClientSyncBackend()
  //  val backendTwo = HttpURLConnectionBackend(customEncodingHandler = {case (is, "") => is})
  //
  //
  //  val request: Request[Either[String, String], Any] = basicRequest
  //    .header(HeaderNames.Accept, MediaType.ApplicationOctetStream.toString())
  //    .get(uri"https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/index.json")
  //
  //  println(s"headers = ${request.headers}")
  //  val response = request.send(backend)
  //  println(response)
}