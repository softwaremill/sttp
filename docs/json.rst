.. _json:

JSON
====

Adding support for JSON (or other format) bodies in requests/responses is a matter of providing a :ref:`body serializer <requestbody_custom>` and/or a :ref:`response body specification <responsebodyspec_custom>`. Both are quite straightforward to implement, so integrating with your favorite JSON library shouldn't be a problem. However, there are some integrations available out-of-the-box.

Also read about :ref:`handling non 2xx responses <responsebodyspec_handlenon2xx>` if you need to unmarshal error responses.

Circe
-----

JSON encoding of bodies and decoding of responses can be handled using `Circe <https://circe.github.io/circe/>`_ by the ``circe`` module. To use add the following dependency to your project::

  "com.softwaremill.sttp" %% "circe" % "1.5.1"

This module adds a method to the request and a function that can be given to a request to decode the response to a specific object::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.circe._
  
  implicit val backend = HttpURLConnectionBackend()
  
  // Assume that there is an implicit circe encoder in scope
  // for the request Payload, and a decoder for the MyResponse
  val requestPayload: Payload = ???
  
  val response: Response[Either[io.circe.Error, MyResponse]] =
    sttp
      .post(uri"...")
      .body(requestPayload)
      .response(asJson[MyResponse])
      .send()

Json4s
------

To encode and decode json using json4s, add the following dependency to your project::

  "com.softwaremill.sttp" %% "json4s" % "1.5.1"
  "org.json4s" %% "json4s-native" % "3.6.0"

Note that in this example we are using the json4s-native backend, but you can use any other json4s backend.

Using this module it is possible to set request bodies and read response bodies as case classes, using the implicitly available ``org.json4s.Formats`` (which defaults to ``org.json4s.DefaultFormats``), and by bringing an implicit ``org.json4s.Serialization`` into scope.

Usage example::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.json4s._
  
  implicit val backend = HttpURLConnectionBackend()

  case class Payload(...)
  case class MyResponse(...)

  val requestPayload: Payload = Payload(...)

  implicit val serialization =  org.json4s.native.Serialization
  
  val response: Response[MyResponse] =
    sttp
      .post(uri"...")
      .body(requestPayload)
      .response(asJson[MyResponse])
      .send()
 
spray-json
----------

To encode and decode JSON using `spray-json <https://github.com/spray/spray-json>`_, add the following dependency to your project::

  "com.softwaremill.sttp" %% "spray-json" % "1.5.1"

Using this module it is possible to set request bodies and read response bodies as your custom types, using the implicitly available instances of ``spray.json.JsonWriter`` / ``spray.json.JsonReader`` or ``spray.json.JsonFormat``.

Usage example::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.sprayJson._
  import spray.json._

  implicit val backend = HttpURLConnectionBackend()

  case class Payload(...)
  object Payload {
    implicit val jsonFormat: RootJsonFormat[Payload] = ...
  }

  case class MyResponse(...)
  object MyResponse {
    implicit val jsonFormat: RootJsonFormat[MyResponse] = ...
  }

  val requestPayload: Payload = Payload(...)

  val response: Response[MyResponse] =
    sttp
      .post(uri"...")
      .body(requestPayload)
      .response(asJson[MyResponse])
      .send()

play-json
----------

To encode and decode JSON using `play-json <https://www.playframework.com/documentat…>`_, add the following dependency to your project::

  "com.softwaremill.sttp" %% "play-json" % "1.5.1"

To use, add an import: ``import com.softwaremill.sttp.playJson._``.
