# Response body descriptions

By default, the received response body will be read as a `Either[String, String]`, using the encoding specified in the `Content-Type` response header (and if none is specified, using `UTF-8`). This is of course configurable: response bodies can be ignored, deserialized into custom types, received as a stream or saved to a file.

The default `response.body` will be a:

* `Left(errorMessage)` if the request is successful, but response code is not 2xx.
* `Right(body)` if the request is successful, and the response code is 2xx.

How the response body will be read is part of the request description, as already when sending the request, the backend needs to know what to do with the response. The type to which the response body should be deserialized is a type parameter of `Request`. It's used in request definition in the `request.response: ResponseAs[T]` property.

## Basic response descriptions

To conveniently specify how to deserialize the response body, a number of `as(...Type...)` methods are available. They can be used to provide a value for the request description's `response` property:

```scala mdoc:compile-only
import sttp.client4._

basicRequest.response(asByteArray)
```

When the above request is completely described and sent, it will result in a `Response[Either[String, Array[Byte]]]` (where the left and right correspond to non-2xx and 2xx status codes, as above). 

Other possible response descriptions include:

```scala mdoc:compile-only
import sttp.client4.*
import java.io.File
import java.nio.file.Path

def ignore: ResponseAs[Unit] = ???
def asString: ResponseAs[Either[String, String]] = ???
def asStringAlways: ResponseAs[String] = ???
def asString(encoding: String): ResponseAs[Either[String, String]] = ???
def asStringAlways(encoding: String): ResponseAs[String] = ???
def asByteArray: ResponseAs[Either[String, Array[Byte]]] = ???
def asByteArrayAlways: ResponseAs[Array[Byte]] = ???
def asParams: ResponseAs[Either[String, Seq[(String, String)]]] = ???
def asParamsAlways: ResponseAs[Seq[(String, String)]] = ???
def asParams(encoding: String): ResponseAs[Either[String, Seq[(String, String)]]] = ???
def asParamsAlways(encoding: String): ResponseAs[Seq[(String, String)]] = ???
def asFile(file: File): ResponseAs[Either[String, File]] = ???
def asFileAlways(file: File): ResponseAs[File] = ???
def asPath(path: Path): ResponseAs[Either[String, Path]] = ???
def asPathAlways(path: Path): ResponseAs[Path] = ???

def asEither[A, B](onError: ResponseAs[A], 
                   onSuccess: ResponseAs[B]): ResponseAs[Either[A, B]] = ???
def fromMetadata[T](default: ResponseAs[T], 
                    conditions: ConditionalResponseAs[T]*): ResponseAs[T] = ???

def asBoth[A, B](l: ResponseAs[A], r: ResponseAs[B]): ResponseAs[(A, B)] = ???
def asBothOption[A, B](l: ResponseAs[A], r: ResponseAs[B]): ResponseAs[(A, Option[B])] = ???
```

Hence, to discard the response body, the request description should include the following:

```scala mdoc:compile-only
import sttp.client4.*

basicRequest.response(ignore)
```   

And to save the response to a file:

```scala mdoc:compile-only
import sttp.client4.*
import java.io.*

val someFile = new File("some/path")
basicRequest.response(asFile(someFile))
```

```{eval-rst}
.. note::

 As the handling of response is specified upfront, there's no need to "consume" the response body. It can be safely discarded if not needed.
```

## Failing when the response code is not 2xx

Sometimes it's convenient to get a failed effect (or an exception thrown) when the response status code is not successful. In such cases, the response description can be modified using the `.orFail` combinator:

```scala mdoc:compile-only
import sttp.client4.*

basicRequest.response(asString.orFail): PartialRequest[String]
```

The combinator works in all cases where the response body is specified to be deserialized as an `Either`. If the left is already an exception, it will be thrown unchanged. Otherwise, the left-value will be wrapped in an `HttpError`.

```{eval-rst}
.. note::

 While both ``asStringAlways`` and ``asString.orFail`` have the type ``ResponseAs[String, Any]``, they are different. The first will return the response body as a string always, regardless of the responses' status code. The second will return a failed effect / throw a ``HttpError`` exception for non-2xx status codes, and the string as body only for 2xx status codes.
```

There's also a variant of the combinator, `.getEither`, which can be used to extract typed errors and fail the effect if there's a deserialization error.

## Custom body deserializers

It's possible to define custom body deserializers by taking any of the built-in response descriptions and mapping over them. Each `ResponseAs` instance has `map` and `mapWithMetadata` methods, which can be used to transform it to a description for another type (optionally using response metadata, such as headers or the status code). Each such value is immutable and can be used multiple times.

```{eval-rst}
.. note:: Alternatively, response descriptions can be modified directly from the request description, by using the ``request.mapResponse(...)`` and ``request.mapResponseRight(...)`` methods (which is available, if the response body is deserialized to an either). That's equivalent to calling ``request.response(request.response.map(...))``, that is setting a new response description, to a modified old response description; but with shorter syntax.
```

As an example, to read the response body as an int, the following response description can be defined (warning: this ignores the possibility of exceptions!):

```scala mdoc:compile-only
import sttp.client4.*

val asInt: ResponseAs[Either[String, Int]] = asString.mapRight((_: String).toInt)

basicRequest
  .get(uri"http://example.com")
  .response(asInt)
```

To integrate with a third-party JSON library, and always parse the response as JSON (regardless of the status code):

```scala mdoc:compile-only
import sttp.client4.*

type JsonError
type JsonAST

def parseJson(json: String): Either[JsonError, JsonAST] = ???
val asJson: ResponseAs[Either[JsonError, JsonAST]] = asStringAlways.map(parseJson)

basicRequest
  .response(asJson)
```

A number of JSON libraries are supported out-of-the-box, see [json support](../json.md).

## Response-metadata dependent deserializers

Using the `fromMetadata` combinator, it's possible to dynamically specify how the response should be deserialized, basing on the response status code and response headers. The default `asString`, `asByteArray` response descriptions use this method to return a `Left` in case of non-2xx responses, and a `Right` otherwise. 

A more complex case, which uses Circe for deserializing JSON, choosing to which model to deserialize to depending on the status code, can look as following:

```scala mdoc:compile-only
import sttp.client4.*
import sttp.model.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*

sealed trait MyModel
case class SuccessModel(name: String, age: Int) extends MyModel
case class ErrorModel(message: String) extends MyModel

val myRequest: Request[Either[ResponseException[String, io.circe.Error], MyModel]] =
  basicRequest
    .get(uri"https://example.com")
    .response(fromMetadata(
        asJson[ErrorModel], 
        ConditionalResponseAs(_.code == StatusCode.Ok, asJson[SuccessModel])
    ))
```

The above example assumes that success and error models are part of one hierarchy (`MyModel`). Sometimes http errors are modelled independently of success. In this case, we can use `asJsonEither`, which uses `asEitherDeserialized` under the covers:

```scala mdoc:compile-only
import sttp.client4.*
import sttp.model.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*

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

### Blocking streaming (InputStream)

Some backends on the JVM support receiving the response body as a `java.io.InputStream`. This is possible either using the safe `asInputStream(f)` description, where the entire stream has to be consumed by the provided `f` function, and is then closed by sttp client. Alternatively, there's `asInputStreamUnsafe`, which returns the stream directly to the user, who is then responsible for closing it.

`InputStream`s have two limitations. First, they operate on the relatively low `byte`-level. The consumer is responsible for any decoding, chunking etc. Moreover, all `InputStream` operations are blocking, hence using them in a non-virtual-threads environment may severely limit performance. If you're using a functional effect system, see below on how to use non-blocking streams instead.

### Non-blocking streaming

If the backend used supports non-blocking, asynchronous streaming (see "Supported stream type" in the [backends summary](../backends/summary.md)), it's possible to receive responses as a stream. This can be described using the following methods:

```scala mdoc:compile-only
import sttp.capabilities.{Effect, Streams}
import sttp.client4.*
import sttp.model.ResponseMetadata

def asStream[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): 
  StreamResponseAs[Either[String, T], Effect[F] with S] = ???

def asStreamWithMetadata[F[_], T, S](s: Streams[S])(
      f: (s.BinaryStream, ResponseMetadata) => F[T] 
  ): StreamResponseAs[Either[String, T], Effect[F] with S] = ???

def asStreamAlways[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): 
  StreamResponseAs[T, Effect[F] with S] = ???

def asStreamAlwaysWithMetadata[F[_], T, S](s: Streams[S])(
      f: (s.BinaryStream, ResponseMetadata) => F[T]
  ): StreamResponseAs[T, Effect[F] with S] = ???

def asStreamUnsafe[S](s: Streams[S]): 
  StreamResponseAs[Either[String, s.BinaryStream], S] = ???

def asStreamUnsafeAlways[S](s: Streams[S]): 
  StreamResponseAs[s.BinaryStream, S] = ???
```

All of these descriptions require the streaming capability to be passed as a parameter, an implementation of `Streams[S]`. This is used to determine the type of binary streams that are supported, and to require that the backend used to send the request supports the given type of streams. These implementations are provided by the backend implementations, e.g. `AkkaStreams` or `Fs2Streams[F]`. 

The first two "safe" variants pass the response stream to the user-provided function, which should consume the stream entirely. Once the effect returned by the function is complete, the backend will try to close the stream (if the streaming implementation allows it).

The "unsafe" variants return the stream directly to the user, and then it's up to the user of the code to consume and close the stream, releasing any resources held by the HTTP connection.

For example, when using the [Pekko backend](../backends/pekko.md):

```scala mdoc:compile-only
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import scala.concurrent.Future
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.*
import sttp.client4.pekkohttp.PekkoHttpBackend

val backend: StreamBackend[Future, PekkoStreams] = PekkoHttpBackend()

val response: Future[Response[Either[String, Source[ByteString, Any]]]] =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(PekkoStreams))
    .send(backend)    
```

It's also possible to parse the received stream as server-sent events (SSE), using an implementation-specific mapping function. Refer to the documentation for particular backends for more details.

## Decompressing bodies (handling the Conent-Encoding header)

If the response body is compressed using `gzip` or `deflate` algorithms, it will be decompressed if the `decompressResponseBody` request option is set. By default this is set to `true`, and can be disabled using the `request.disableAutoDecompression` method.

The encoding of the response body is determined by the encodings that are accepted by the client. That's why `basicRequest` and `quickRequest` both have the `Accept-Encoding` header set to `gzip, deflate`. That's in contrast to `emptyRequest`, which has no headers set by default.

If you'd like to use additional decompression algorithms, you'll need to:

* amend the `Accept-Encoding` header that's set on the request
* add a decompression algorithm to the backend; that can be done on backend creation time, by customising the `compressionHandlers` parameter, and adding a `Decompressor` implementation. Such an implementation has to specify the encoding, which it handles, as well as appropriate body transformation (which is backend-specific).