# Response body specification

By default, the received response body will be read as a `Either[String, String]`, using the encoding specified in the `Content-Type` response header (and if none is specified, using `UTF-8`). This is of course configurable: response bodies can be ignored, deserialized into custom types, received as a stream or saved to a file.

The default `response.body` will be a:

* `Left(errorMessage)` if the request is successful, but response code is not 2xx.
* `Right(body)` if the request is successful, and the response code is 2xx.

How the response body will be read is part of the request description, as already when sending the request, the backend needs to know what to do with the response. The type to which the response body should be deserialized is the second type parameter of `RequestT`, and stored in the request definition as the `request.response: ResponseAs[T, R]` property.

## Basic response specifications

To conveniently specify how to deserialize the response body, a number of `as(...Type...)` methods are available. They can be used to provide a value for the request description's `response` property:

```scala
import sttp.client._

basicRequest.response(asByteArray)
```

When the above request is completely described and sent, it will result in a `Response[Either[String, Array[Byte]]]` (where the left and right correspond to non-2xx and 2xx status codes, as above). 

Other possible response descriptions include (the first type parameter of `ResponseAs` specifies the type returned as the response body, the second - the capabilities that the backend is required to support to send the request; `Any` means no special requirements):

```scala
import sttp.client._
import java.io.File
import java.nio.file.Path

def ignore: ResponseAs[Unit, Any] = ???
def asString: ResponseAs[Either[String, String], Any] = ???
def asStringAlways: ResponseAs[String, Any] = ???
def asString(encoding: String): ResponseAs[Either[String, String], Any] = ???
def asStringAlways(encoding: String): ResponseAs[String, Any] = ???
def asByteArray: ResponseAs[Either[String, Array[Byte]], Any] = ???
def asByteArrayAlways: ResponseAs[Array[Byte], Any] = ???
def asParams: ResponseAs[Either[String, Seq[(String, String)]], Any] = ???
def asParamsAlways: ResponseAs[Seq[(String, String)], Any] = ???
def asParams(encoding: String): ResponseAs[Either[String, Seq[(String, String)]], Any] = ???
def asParamsAlways(encoding: String): ResponseAs[Seq[(String, String)], Any] = ???
def asFile(file: File): ResponseAs[Either[String, File], Any] = ???
def asFileAlways(file: File): ResponseAs[File, Any] = ???
def asPath(path: Path): ResponseAs[Either[String, Path], Any] = ???
def asPathAlways(path: Path): ResponseAs[Path, Any] = ???

def asEither[A, B, R](onError: ResponseAs[A, R], 
                      onSuccess: ResponseAs[B, R]): ResponseAs[Either[A, B], R] = ???
def fromMetadata[T, R](default: ResponseAs[T, R], 
                       conditions: ConditionalResponseAs[T, R]*): ResponseAs[T, R] = ???
```

Hence, to discard the response body, the request description should include the following:

```scala
import sttp.client._

basicRequest.response(ignore)
```   

And to save the response to a file:

```scala
import sttp.client._
import java.io._

val someFile = new File("some/path")
basicRequest.response(asFile(someFile))
```

```note:: As the handling of response is specified upfront, there's no need to "consume" the response body. It can be safely discarded if not needed.

```

## Failing when the response code is not 2xx

Sometimes it's convenient to get a failed effect (or an exception thrown) when the response status code is not successful. In such cases, the response specification can be modified using the `.failLeft` combinator:

```scala
import sttp.client._

basicRequest.response(asString.failLeft): PartialRequest[String, Any]
```

The combinator works in all cases where the response body is specified to be deserialized as an `Either`. If the left is already an exception, it will be thrown unchanged. Otherwise, the left-value will be wrapped in an `HttpError`.

```note:: While both ``asStringAlways`` and ``asString.failLeft`` have the type ``ResponseAs[String, Any]``, they are different. The first will return the response body as a string always, regardless of the responses' status code. The second will return a failed effect / throw a ``HttpError`` exception for non-2xx status codes, and the string as body only for 2xx status codes.```

There's also a variant of the combinator, `.failLeftDeserialize`, which can be used to extract typed errors and fail the effect if there's a deserialization error.

## Custom body deserializers

It's possible to define custom body deserializers by taking any of the built-in response descriptions and mapping over them. Each `ResponseAs` instance has `map` and `mapWithMetadata` methods, which can be used to transform it to a description for another type (optionally using response metadata, such as headers or the status code). Each such value is immutable and can be used multiple times.

```eval_rst
.. note:: Alternatively, response descriptions can be modified directly from the request description, by using the ``request.mapResponse(...)`` and ``request.mapResponseRight(...)`` methods (which is available, if the response body is deserialized to an either). That's equivalent to calling ``request.response(request.response.map(...))``, that is setting a new response description, to a modified old response description; but with shorter syntax.
```

As an example, to read the response body as an int, the following response description can be defined (warning: this ignores the possibility of exceptions!):

```scala
import sttp.client._

val asInt: ResponseAs[Either[String, Int], Any] = asString.mapRight(_.toInt)

basicRequest
  .get(uri"http://example.com")
  .response(asInt)
```

To integrate with a third-party JSON library, and always parse the response as a json (regardless of the status code):

```scala
import sttp.client._

type JsonError
type JsonAST

def parseJson(json: String): Either[JsonError, JsonAST] = ???
val asJson: ResponseAs[Either[JsonError, JsonAST], Any] = asStringAlways.map(parseJson)

basicRequest
  .response(asJson)
```

A number of JSON libraries are supported out-of-the-box, see [json support](../json.md).

### Response-metadata dependent deserializers

Using the `fromMetadata` combinator, it's possible to dynamically specify how the response should be deserialized, basing on the response status code and response headers. The default `asString`, `asByteArray` response descriptions use this method to return a `Left` in case of non-2xx responses, and a `Right` otherwise. 

A more complex case, which uses Circe for deserializing JSON, choosing to which model to deserialize to depending on the status code, can look as following:

```scala
import sttp.client._
import sttp.model._
import sttp.client.circe._
import io.circe._
import io.circe.generic.auto._

sealed trait MyModel
case class SuccessModel(name: String, age: Int) extends MyModel
case class ErrorModel(message: String) extends MyModel

val myRequest: Request[Either[ResponseException[String, io.circe.Error], MyModel], Nothing] =
  basicRequest
    .get(uri"https://example.com")
    .response(fromMetadata(
        asJson[ErrorModel], 
        ConditionalResponseAs(_.code == StatusCode.Ok, asJson[SuccessModel])
    ))
```

The above example assumes that success and error models are part of one hierarchy (`MyModel`). Sometimes http errors are modelled independently of success. In this case, we can use `asJsonEither`, which uses `asEitherDeserialized` under the covers:

```scala
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
  .response(asJsonEither[MyErrorModel, MyModel])
```

## Streaming

If the backend used supports streaming (see [backends summary](../backends/summary.md)), it's possible to receive responses as a stream. This can be described using the following methods:

```scala
import sttp.capabilities.{Effect, Streams}
import sttp.client._

def asStream[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): 
  ResponseAs[Either[String, T], Effect[F] with S] = ???

def asStreamAlways[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): 
  ResponseAs[T, Effect[F] with S] = ???

def asStreamUnsafe[S](s: Streams[S]): 
  ResponseAs[Either[String, s.BinaryStream], S] = ???

def asStreamUnsafeAlways[S](s: Streams[S]): 
  ResponseAs[s.BinaryStream, S] = ???
```

All of these specifications require the streaming capability to be passes as a parameter, an implementation of `Streams[S]`. This is used to determine the type of binary streams that are supported, and to require that the backend used to send the request supports the given type of streams. These implementations are provided by the backend implementations, e.g. `AkkaStreams` or `Fs2Streams[F]`. 

The first two "safe" variants passes the response stream to the user-provided function, which should consume the stream entirely. Once the effect returned by the function is complete, the backend will try to close the stream (if the streaming implementation allows it).

The "unsafe" variants return the stream directly to the user, and then it's up to the user of the code to consume and close the stream, releasing any resources held by the HTTP connection.

For example, when using the [Akka backend](../backends/akka.md):

```scala
import akka.stream.scaladsl.Source
import akka.util.ByteString
import scala.concurrent.Future
import sttp.capabilities.akka.AkkaStreams
import sttp.client._
import sttp.client.akkahttp.AkkaHttpBackend

val backend: SttpBackend[Future, AkkaStreams] = AkkaHttpBackend()

val response: Future[Response[Either[String, Source[ByteString, Any]]]] =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(AkkaStreams))
    .send(backend)
```
