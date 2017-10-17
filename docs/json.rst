.. _json:

JSON
====

JSON encoding of bodies and decoding of responses can be handled using 
`Circe <https://circe.github.io/circe/>`_ by the ``circe`` module. To use
add the following dependency to your project::

  "com.softwaremill.sttp" %% "circe" % "0.0.20"

This module adds a method to the request and a function that can be given to
a request to decode the response to a specific object::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.circe._
  
  implicit val backend = HttpURLConnectionBackend()
  
  // Assume that there is an implicit circe encoder in scope
  // for the request Payload, and a decoder for the Response
  val requestPayload: Payload = ???
  
  val response: Either[io.circe.Error, Response] = 
    sttp
      .post(uri"...")
      .body(requestPayload)
      .response(asJson[Response])
      .send()

    
