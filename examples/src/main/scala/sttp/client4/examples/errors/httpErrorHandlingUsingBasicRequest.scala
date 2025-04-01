// {cat=Error handling; effects=Direct; backend=HttpClient}: HTTP error handling using basicRequest

//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC3

package sttp.client4.examples

import sttp.client4.*

@main def httpErrorHandlingUsingBasicRequest(): Unit =
  val backend = DefaultSyncBackend()

  // using basicRequest, the response is read into an Either[String, String], to
  // indicate HTTP success or error
  def httpSuccess(): Unit =
    val response = basicRequest.get(uri"https://httpbin.org/ip").send(backend)
    val body: Either[String, String] = response.body
    println(s"HTTP success, response code ${response.code}, body: $body")

  def httpError(): Unit =
    val response = basicRequest.get(uri"https://httpbin.org/status/400").send(backend)
    val body: Either[String, String] = response.body
    println(s"HTTP error, response code ${response.code}, body: ${body}")

  def connectionException(): Unit =
    try println(basicRequest.get(uri"http://does.not.exist").send(backend))
    catch case e: SttpClientException.ConnectException => println(s"Connection exception: $e")

  httpSuccess()
  httpError()
  connectionException()
