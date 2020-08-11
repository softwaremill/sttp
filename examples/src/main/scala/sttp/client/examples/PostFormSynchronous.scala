package sttp.client.examples

object PostFormSynchronous extends App {
  import sttp.client._

  val signup = Some("yes")

  val request = basicRequest
    // send the body as form data (x-www-form-urlencoded)
    .body(Map("name" -> "John", "surname" -> "doe"))
    // use an optional parameter in the URI
    .post(uri"https://httpbin.org/post?signup=$signup")

  val backend = HttpURLConnectionBackend()
  val response = request.send(backend)

  println(response.body)
  println(response.headers)
}
