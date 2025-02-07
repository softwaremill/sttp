// {cat=Error handling; effects=Direct; backend=HttpClient}: HTTP error handling, adjusting the response description

//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC1

package sttp.client4.examples

import sttp.client4.*

@main def httpErrorHandlingAdjustResponse(): Unit =
  val backend = DefaultSyncBackend()

  // by default the response is read into an Either[String, String], to indicate HTTP success or error
  def httpErrorDefault(): Unit =
    val response = basicRequest.get(uri"https://httpbin.org/status/400").send(backend)
    val body: Either[String, String] = response.body
    println(s"HTTP error, response code ${response.code}, body: ${body}")

  // using asStringOrFail, any HTTP-level errors (successful connection, but a non-2xx status code)
  // are translated into exceptions
  def httpErrorOrFail(): Unit =
    try
      val _ = basicRequest.get(uri"https://httpbin.org/status/400").response(asStringOrFail).send(backend)
    catch case e: SttpClientException.ReadException => println(s"Connection exception: $e")

  httpErrorDefault()
  httpErrorOrFail()
