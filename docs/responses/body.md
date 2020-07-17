# Response body specification

By default, the received response body will be read as a `Either[String, String]`, using the encoding specified in the `Content-Type` response header (and if none is specified, using `UTF-8`). This is of course configurable: response bodies can be ignored, deserialized into custom types, received as a stream or saved to a file.

The default `response.body` will be a:

* `Left(errorMessage)` if the request is successful, but response code is not 2xx.
* `Right(body)` if the request is successful and the response code is 2xx.

How the response body will be read is part of the request description, as already when sending the request, the backend needs to know what to do with the response. The type to which the response body should be deserialized is the second type parameter of `RequestT`, and stored in the request definition as the `request.response: ResponseAs[T, S]` property.

## Basic response specifications

To conveniently specify how to deserialize the response body, a number of `as[Type]` methods are available. They can be used to provide a value for the request description's `response` property:

```scala mdoc:compile-only
import sttp.client._

basicRequest.response(asByteArray)
```

When the above request is completely described and sent, it will result in a `Response[Either[String, Array[Byte]]]` (where the left and right correspond to non-2xx and 2xx status codes, as above). Other possible response descriptions are:

```scala
def ignore: ResponseAs[Unit, Nothing]
def asString: ResponseAs[Either[String, String], Nothing]
def asStringAlways: ResponseAs[String, Nothing]
def asString(encoding: String): ResponseAs[Either[String, String], Nothing]
def asStringAlways(encoding: String): ResponseAs[String, Nothing]
def asByteArray: ResponseAs[Either[String, Array[Byte]], Nothing]
def asByteArrayAlways: ResponseAs[Array[Byte], Nothing]
def asParams: ResponseAs[Either[String, Seq[(String, String)]], Nothing]
def asParams(encoding: String): ResponseAs[Either[String, Seq[(String, String)]], Nothing]
def asFile(file: File): ResponseAs[Either[String, File], Nothing]
def asFileAlways(file: File): ResponseAs[File, Nothing]
def asPath(path: Path): ResponseAs[Either[String, Path], Nothing]
def asPathAlways(path: Path): ResponseAs[Path, Nothing]

def asEither[L, R, S](onError: ResponseAs[L, S], 
                      onSuccess: ResponseAs[R, S]): ResponseAs[Either[L, R], S]
def fromMetadata[T, S](f: ResponseMetadata => ResponseAs[T, S]): ResponseAs[T, S]
```

Hence, to discard the response body, the request description should include the following:

```scala mdoc:compile-only
import sttp.client._

basicRequest.response(ignore)
```   

And to save the response to a file:

```scala mdoc:compile-only
import sttp.client._
import java.io._

val someFile = new File("some/path")
basicRequest.response(asFile(someFile))
```

```note:: As the handling of response is specified upfront, there's no need to "consume" the response body. It can be safely discarded if not needed.

```

## Custom body deserializers

It's possible to define custom body deserializers by taking any of the built-in response descriptions and mapping over them. Each `ResponseAs` instance has `map` and `mapWithMetadata` methods, which can be used to transform it to a description for another type (optionally using response metadata, such as headers or the status code). Each such value is immutable and can be used multiple times.

```eval_rst
.. note:: Alternatively, response descriptions can be modified directly from the request description, by using the ``request.mapResponse(...)`` and ``request.mapResponseRight(...)`` methods (which is available, if the response body is deserialized to an either). That's equivalent to calling ``request.response(request.response.map(...))``, that is setting a new response description, to a modified old response description; but with shorter syntax.
```

As an example, to read the response body as an int, the following response description can be defined (warning: this ignores the possibility of exceptions!):

```scala mdoc:compile-only
import sttp.client._

val asInt: ResponseAs[Either[String, Int], Nothing] = asString.mapRight(_.toInt)

basicRequest
  .get(uri"http://example.com")
  .response(asInt)
```

To integrate with a third-party JSON library, and always parse the response as a json (regardless of the status code):

```scala
def parseJson(json: String): Either[JsonError, JsonAST] = ...
val asJson: ResponseAs[Either[JsonError, JsonAST], Nothing] = asStringAlways.map(parseJson)

basicRequest
  .response(asJson)
```

A number of JSON libraries are supported out-of-the-box, see [json support](../json.md).

Sometimes it might be useful to model some http error responses right away. We can do that by using `either` combined with `fromStatusCodes`:
```scala mdoc:compile-only
import sttp.client._
import sttp.model._
import sttp.client.circe._
import io.circe._
import io.circe.generic.auto._

case class MyModel(p1: Int)
sealed trait MyErrorModel
case class Conflict(message: String) extends MyErrorModel
case class BadRequest(message: String) extends MyErrorModel
case class GenericError(message: String) extends MyErrorModel

basicRequest
  .get(uri"https://example.com")
  .response(either(fromStatusCode{
    case StatusCode.Conflict => asJsonAlways[Conflict]
    case StatusCode.BadRequest => asJsonAlways[BadRequest]
    case _ => asStringAlways.map(s=>Right(GenericError(s)))
  }, asJsonAlways[MyModel]))
```

There is also an unsafe variant of above method (`eitherUnsafe`) which in case of deserialization error or any unspecified http error throws related exception.

Using the `fromMetadata` combinator, it's possible to dynamically specify how the response should be deserialized, basing on the response status code and response headers. The default `asString`, `asByteArray` response descriptions use this method to return a `Left` in case of non-2xx responses, and a `Right` otherwise. 

A more complex case, which uses Circe for deserializing JSON, choosing to which model to deserialize to depending on the status code, can look as following:

```scala mdoc:compile-only
import sttp.client._
import sttp.model._
import sttp.client.circe._
import io.circe._
import io.circe.generic.auto._

sealed trait MyModel
case class SuccessModel(name: String, age: Int) extends MyModel
case class ErrorModel(message: String) extends MyModel

val myRequest: Request[Either[ResponseError[io.circe.Error], MyModel], Nothing] =
  basicRequest
    .get(uri"https://example.com")
    .response(fromMetadata { meta =>
      meta.code match {
        case StatusCode.Ok => asJson[SuccessModel]
        case _             => asJson[ErrorModel]
      }
    })
```

## Streaming

If the backend used supports streaming (see [backends summary](../backends/summary.md)), it's possible to receive responses as a stream. This can be described using the following methods:

```scala
def asStream[S]: ResponseAs[Either[String, S], S] = ResponseAsStream[S, S]()
def asStreamAlways[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()
```

For example, when using the [Akka backend](../backends/akka.md):

```scala mdoc:compile-only
import akka.stream.scaladsl.Source
import akka.util.ByteString
import scala.concurrent.Future
import sttp.client._
import sttp.client.akkahttp._

implicit val sttpBackend: SttpBackend[Future, Source[ByteString, Any], NothingT] = AkkaHttpBackend()

val response: Future[Response[Either[String, Source[ByteString, Any]]]] =
  basicRequest
    .post(uri"...")
    .response(asStream[Source[ByteString, Any]])
    .send()    
```

```note:: Unlike with non-streaming response handlers, each streaming response should be entirely consumed by client code.

```
