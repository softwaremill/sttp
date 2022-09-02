package sttp.client3.examples

import sttp.client3.{Response, SttpClient, UriContext, asStringAlways, basicRequest}

import java.util.UUID

object SimpleClientGetAndPost extends App {
  val client = SttpClient()

  val response: Response[Either[String, String]] = basicRequest
    .get(uri"https://httpbin.org/get")
    .send(client)

  response.body match {
    case Left(body)  => println(s"Non-2xx response to GET with code ${response.code}:\n$body")
    case Right(body) => println(s"2xx response to GET:\n$body")
  }

  println("---\n")

  //

  val response2: Response[String] = basicRequest
    .header("X-Correlation-ID", UUID.randomUUID().toString)
    .response(asStringAlways)
    .body("Hello, world!")
    .post(uri"https://httpbin.org/post")
    .send(client)

  println(s"Response to POST:\n${response2.body}")
}
