// {cat=Error handling; effects=Direct; backend=HttpClient}: Parsing the response as JSON, with parsing failures and HTTP errors

//> using dep com.softwaremill.sttp.client4::jsoniter:4.0.14
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.33.3

package sttp.client4.examples

import sttp.client4.*
import sttp.client4.jsoniter.*
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec

@main def httpErrorHandlingJson(): Unit =
  val backend = DefaultSyncBackend()

  case class HttpBinIpResponse(origin: String) derives ConfiguredJsonValueCodec
  case class HttpBinIpInvalidResponse(myIp: String) derives ConfiguredJsonValueCodec

  // the class to which we want to parse (HttpBinIpResponse) matches the response's format - we'll get the result as a Right(...)
  def jsonSuccess(): Unit =
    val response = basicRequest.get(uri"https://httpbin.org/ip").response(asJson[HttpBinIpResponse]).send(backend)
    val body: Either[ResponseException[String], HttpBinIpResponse] = response.body
    println(s"JSON success, response code ${response.code}, body: $body")

  // the class to which we want to parse (HttpBinIpInvalidResponse) does not match the response's format - we'll get a Left(DeserializationException)
  def jsonParsingFailure(): Unit =
    val response =
      basicRequest.get(uri"https://httpbin.org/ip").response(asJson[HttpBinIpInvalidResponse]).send(backend)
    val body: Either[ResponseException[String], HttpBinIpInvalidResponse] = response.body
    println(s"JSON parsing failure, response code ${response.code}, body: $body")

  // http error - status code 400 - we'll get Left(HttpError)
  def httpError(): Unit =
    val response =
      basicRequest.get(uri"https://httpbin.org/status/400").response(asJson[HttpBinIpResponse]).send(backend)
    val body: Either[ResponseException[String], HttpBinIpResponse] = response.body
    println(s"HTTP error, response code ${response.code}, body: $body")

  // when using asJsonOrFail response description, any parsing failures are thrown as exceptions
  def jsonParsingFailureThrowingException(): Unit =
    try
      val response =
        basicRequest.get(uri"https://httpbin.org/ip").response(asJsonOrFail[HttpBinIpInvalidResponse]).send(backend)
      val body: HttpBinIpInvalidResponse = response.body
      println(s"We should never get here! Body: $body")
    catch
      case e: SttpClientException.ReadException =>
        println(s"Got exception: $e")

  jsonSuccess()
  jsonParsingFailure()
  httpError()
  jsonParsingFailureThrowingException()
