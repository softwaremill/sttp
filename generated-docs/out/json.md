# JSON

Adding support for JSON (or other format) bodies in requests/responses is a matter of providing a [body serializer](requests/body.md) and/or a [response body specification](responses/body.md). Both are quite straightforward to implement, so integrating with your favorite JSON library shouldn't be a problem. However, there are some integrations available out-of-the-box.

Each integration is available as an import, which brings the implicit `BodySerializer`s and `asJson` methods into scope. Alternatively, these values are grouped intro traits (e.g. `sttp.client3.circe.SttpCirceApi`), which can be extended to group multiple integrations in one object, and thus reduce the number of necessary imports.

The following variants of `asJson` methods are available:

* regular - deserializes the body to json, only if the response is successful (2xx)
* `always` - deserializes the body to json regardless of the status code
* `either` - uses different deserializers for error and successful (2xx) responses

The type signatures vary depending on the underlying library (required implicits and error representation differs), but they obey the following pattern:

```scala
import sttp.client3._

def asJson[B]: ResponseAs[Either[ResponseException[String, Exception], B], Any] = ???
def asJsonAlways[B]: ResponseAs[Either[DeserializationException[Exception], B], Any] = ???
def asJsonEither[E, B]: ResponseAs[Either[ResponseException[E, Exception], B], Any] = ???
```

The response specifications can be further refined using `.getRight` and `.getEither`, see [response body specifications](responses/body.md).

Following data class will be used through the next few examples:

```scala
case class RequestPayload(data: String)
case class ResponsePayload(data: String)
```

## Circe

JSON encoding of bodies and decoding of responses can be handled using [Circe](https://circe.github.io/circe/) by the `circe` module. To use add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "circe" % "3.3.10"
```

This module adds a body serialized, so that json payloads can be sent as request bodies. To send a payload of type `T` as json, a `io.circe.Encoder[T]` implicit value must be available in scope.
Automatic and semi-automatic derivation of encoders is possible by using the [circe-generic](https://circe.github.io/circe/codec.html) module. 
 
Response can be parsed into json using `asJson[T]`, provided there's an implicit `io.circe.Decoder[T]` in scope. The decoding result will be represented as either a http/deserialization error, or the parsed value. For example:

```scala
import sttp.client3._
import sttp.client3.circe._

val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

import io.circe.generic.auto._
val requestPayload = RequestPayload("some data")

val response: Identity[Response[Either[ResponseException[String, io.circe.Error], ResponsePayload]]] =
  basicRequest
    .post(uri"...")
    .body(requestPayload)
    .response(asJson[ResponsePayload])
    .send(backend)
```

Arbitrary JSON structures can be traversed by parsing the result as `io.circe.Json`, and using the [circe-optics](https://circe.github.io/circe/optics.html) module.

## Json4s

To encode and decode json using json4s, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "json4s" % "3.3.10"
"org.json4s" %% "json4s-native" % "3.6.0"
```

Note that in this example we are using the json4s-native backend, but you can use any other json4s backend.

Using this module it is possible to set request bodies and read response bodies as case classes, using the implicitly available `org.json4s.Formats` (which defaults to `org.json4s.DefaultFormats`), and by bringing an implicit `org.json4s.Serialization` into scope.

Usage example:

```scala
import sttp.client3._
import sttp.client3.json4s._

val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

val requestPayload = RequestPayload("some data")

implicit val serialization = org.json4s.native.Serialization
implicit val formats = org.json4s.DefaultFormats

val response: Identity[Response[Either[ResponseException[String, Exception], ResponsePayload]]] =
  basicRequest
    .post(uri"...")
    .body(requestPayload)
    .response(asJson[ResponsePayload])
    .send(backend)
```

## spray-json

To encode and decode JSON using [spray-json](https://github.com/spray/spray-json), add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "spray-json" % "3.3.10"
```

Using this module it is possible to set request bodies and read response bodies as your custom types, using the implicitly available instances of `spray.json.JsonWriter` / `spray.json.JsonReader` or `spray.json.JsonFormat`.

Usage example:

```scala
import sttp.client3._
import sttp.client3.sprayJson._
import spray.json._

val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

implicit val payloadJsonFormat: RootJsonFormat[RequestPayload] = ???
implicit val myResponseJsonFormat: RootJsonFormat[ResponsePayload] = ???

val requestPayload = RequestPayload("some data")

val response: Identity[Response[Either[ResponseException[String, Exception], ResponsePayload]]] =
  basicRequest
    .post(uri"...")
    .body(requestPayload)
    .response(asJson[ResponsePayload])
    .send(backend)
```

## play-json

To encode and decode JSON using [play-json](https://www.playframework.com), add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "play-json" % "3.3.10"
```

To use, add an import: `import sttp.client3.playJson._`.

## zio-json

To encode and decode JSON using the high-performance [zio-json](https://zio.github.io/zio-json/) library, one add the following dependency to your project.

```scala
"com.softwaremill.sttp.client3" %% "zio-json" % "3.3.10"
```
or for ScalaJS (cross build) projects:
```scala
"com.softwaremill.sttp.client3" %%% "zio-json" % "3.3.10"
```

To use, add an import: `import sttp.client3.ziojson._` (or extend `SttpZioJsonApi`), define an implicit `JsonCodec`, or `JsonDecoder`/`JsonEncoder` for your datatype.

Usage example:

```scala
import sttp.client3._
import sttp.client3.ziojson._
import zio.json._

val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

implicit val payloadJsonEncoder: JsonEncoder[RequestPayload] = DeriveJsonEncoder.gen[RequestPayload]
implicit val myResponseJsonDecoder: JsonDecoder[ResponsePayload] = DeriveJsonDecoder.gen[ResponsePayload]

val requestPayload = RequestPayload("some data")

val response: Identity[Response[Either[ResponseException[String, String], ResponsePayload]]] =
basicRequest
  .post(uri"...")
  .body(requestPayload)
  .response(asJson[ResponsePayload])
  .send(backend)
```