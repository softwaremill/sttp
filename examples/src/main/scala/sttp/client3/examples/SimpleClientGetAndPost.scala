package sttp.client3.examples

import sttp.client3.{Request, Response, SimpleHttpClient, UriContext, asStringAlways, basicRequest}

import java.util.UUID

object SimpleClientGetAndPost extends App {
  val client = SimpleHttpClient()

  try {
    val response: Response[Either[String, String]] = client.send(basicRequest.get(uri"https://httpbin.org/get"))

    response.body match {
      case Left(body)  => println(s"Non-2xx response to GET with code ${response.code}:\n$body")
      case Right(body) => println(s"2xx response to GET:\n$body")
    }

    println("---\n")

    //

    val request2: Request[String, Any] = basicRequest
      .header("X-Correlation-ID", UUID.randomUUID().toString)
      .response(asStringAlways)
      .body("Hello, world!")
      .post(uri"https://httpbin.org/post")
    val response2: Response[String] = client.send(request2)

    println(s"Response to POST:\n${response2.body}")

  } finally client.close()
}
