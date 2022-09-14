# Simple synchronous client

The core module of sttp client includes a simple, synchronous client, which can be used to send requests without
the need to choose or explicitly create a backend.

A simple request can be sent as follows:

```scala mdoc:compile-only
import sttp.client3.{SimpleHttpClient, UriContext, basicRequest}

val client = SimpleHttpClient()
val response = client.send(basicRequest.get(uri"https://httpbin.org/get"))
println(response.body)
```

Creating a client allocates resources (such as selector threads / connection pools), so when it's no longer needed, it 
should be closed using `.close()`. Typically, you should have one client instance for your entire application.

## Serialising and parsing JSON

To serialize a custom type to a JSON body, or to deserialize the response body that is in the JSON format, you'll need
to add an integration with a JSON library. See [json](json.md) for a list of available libraries.

As an example, to integrate with the [uPickle](https://github.com/com-lihaoyi/upickle) library, add the following
dependency:

```scala
"com.softwaremill.sttp.client3" %% "upickle" % "@VERSION@"
```

Your code might then look as follows:

```scala mdoc:compile-only
import sttp.client3.{SimpleHttpClient, UriContext, basicRequest}
import sttp.client3.upicklejson._
import upickle.default._

val client = SimpleHttpClient()

case class MyRequest(field1: String, field2: Int)
// selected fields from the JSON that is being returned by httpbin
case class HttpBinResponse(origin: String, headers: Map[String, String])

implicit val myRequestRW: ReadWriter[MyRequest] = macroRW[MyRequest]
implicit val responseRW: ReadWriter[HttpBinResponse] = macroRW[HttpBinResponse]

val request = basicRequest
  .post(uri"https://httpbin.org/post")
  .body(MyRequest("test", 42))
  .response(asJson[HttpBinResponse])
val response = client.send(request)

response.body match {
  case Left(e)  => println(s"Got response exception:\n$e")
  case Right(r) => println(s"Origin's ip: ${r.origin}, header count: ${r.headers.size}")
}
```

## Adding logging

Logging can be added using the [logging backend wrapper](backends/wrappers/logging.md). For example, if you'd like to
use slf4j, you'll need the following dependency:

```
"com.softwaremill.sttp.client3" %% "slf4j-backend" % "@VERSION@"
```

Then, you'll need to configure your client:

```scala mdoc:compile-only
import sttp.client3.{SimpleHttpClient, UriContext, basicRequest}
import sttp.client3.logging.slf4j.Slf4jLoggingBackend

val client = SimpleHttpClient().wrapBackend(Slf4jLoggingBackend(_))
```

## Relationship with backends

The `SimpleHttpClient` serves as a simple starting point for sending requests in a synchronous way. For more advanced 
use-cases, you should use an [sttp backend](backends/summary.md) directly. For example, if you'd like to send requests 
asynchronously, getting a `Future` as the result. Or, if you manage side effects using an `IO` or `Task`.

In fact, an instance of `SimpleHttpClient` is a thin wrapper on top of a backend.

## Next steps

Read on [how sttp client works](how.md) or see some more [examples](examples.md).