.. _json:

JSON
====

Adding support for JSON (or other format) bodies in requests/responses is a matter of providing a :ref:`body serializer <requestbody_custom>` and/or a :ref:`response body specification <responsebodyspec_custom>`. Both are quite straightforward to implement, so integrating with your favorite JSON library shouldn't be a problem. However, there are some integrations available out-of-the-box.

Circe
-----

JSON encoding of bodies and decoding of responses can be handled using `Circe <https://circe.github.io/circe/>`_ by the ``circe`` module. To use add the following dependency to your project::

  "com.softwaremill.sttp" %% "circe" % "1.0.1"

This module adds a method to the request and a function that can be given to a request to decode the response to a specific object::

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

Json4s
------

To encode and decode json using json4s-native, add the following dependency to your project::

  "com.softwaremill.sttp" %% "json4s" % "1.0.1"

Using this module it is possible to set request bodies and read response bodies as case classes, using the implicitly available ``org.json4s.Formats`` (which defaults to ``org.json4s.DefaultFormats``).

Usage example::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.json4s._
  
  implicit val backend = HttpURLConnectionBackend()

  case class Payload(...)
  val requestPayload: Payload = Payload(...)
  
  val response: Response[Payload] = 
    sttp
      .post(uri"...")
      .body(requestPayload)
      .response(asJson[Response])
      .send()
 
