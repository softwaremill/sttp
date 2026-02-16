# Quickstart

The core sttp client API comes in a single jar, with a transitive dependency on [sttp model](https://github.com/softwaremill/sttp-model). 
This also includes [synchronous](backends/synchronous.md) and [`Future`-based](backends/future.md) backends, based on Java's `HttpClient`.

To integrate with other parts of your application and various effect systems, you'll often need to use an alternate backend, or backend wrappers (but what's important is that the API remains the same!). See the section on [backends](backends/summary.md) for a short guide on which backend to choose, and a list of all implementations.

sttp client is available for Scala 2.12, 2.13 and 3, on the JVM (Java 11+), Scala.JS and Scala Native platforms. Note that not all modules are compatible with these platforms, and that each has its own dedicated set of backends.

## Using sbt

The basic dependency which provides the API, together with a synchronous and `Future`-based backends, is:

```scala
"com.softwaremill.sttp.client4" %% "core" % "4.0.17"
```

## Using scala-cli

Add the following directive to the top of your scala file to add the core sttp dependency:

```
//> using dep com.softwaremill.sttp.client4::core:4.0.17
```

## Using Ammonite

If you are an [Ammonite](https://ammonite.io) user, you can quickly start experimenting with sttp by copy-pasting the following:

```scala
import $ivy.`com.softwaremill.sttp.client4::core:4.0.17`
```

## Imports

Working with sttp is most convenient if you import the `sttp.client4` package entirely:

```scala
import sttp.client4.*
```

This brings into scope the starting point for defining requests (`basicRequest`) and some helper methods. All examples in this guide assume that this import is in place.

## Synchronous requests

And that's all you need to start using sttp client! To create and send your first request, import the above, type `basicRequest.` and see where your IDE's auto-complete gets you! Here's a simple request, using the synchronous backend:

```scala
import sttp.client4.*

val backend = DefaultSyncBackend()
val response = basicRequest
  .body("Hello, world!")  
  .post(uri"https://httpbin.org/post?hello=world")
  .send(backend)

println(response.body)            
```

Creating a backend allocates resources (such as selector threads / connection pools), so when it's no longer needed, it
should be closed using `.close()`. Typically, you should have one backend instance for your entire application.

In the example above, the `response.body` is an `Either[String, String]`. A left value indicates HTTP error (4xx or 5xx) 
response, while a right value indicates HTTP success (2xx). In case of connection errors, an exception is thrown.

You can customize how the response body is handled using [response body handling descriptions](responses/body.md).

## Serializing and parsing JSON

To serialize a custom type to a JSON body, or to deserialize the response body that is in the JSON format, you'll need
to add an integration with a JSON library. See [json](other/json.md) for a list of available libraries.

As an example, to integrate with the [uPickle](https://github.com/com-lihaoyi/upickle) library, add the following
dependency:

```scala
"com.softwaremill.sttp.client4" %% "upickle" % "4.0.17"
```

Your code might then look as follows:

```scala
//> using dep com.softwaremill.sttp.client4::core:4.0.17
//> using dep com.softwaremill.sttp.client4::upickle:4.0.17

import sttp.client4.*
import sttp.client4.upicklejson.default.*
import upickle.default.*

@main def run(): Unit =
  val backend = DefaultSyncBackend()

  case class MyRequest(field1: String, field2: Int)
  // selected fields from the JSON that is being returned by httpbin
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  given ReadWriter[MyRequest] = macroRW[MyRequest]
  given ReadWriter[HttpBinResponse] = macroRW[HttpBinResponse]

  val request = basicRequest
    .post(uri"https://httpbin.org/post")
    .body(asJson(MyRequest("test", 42)))
    .response(asJson[HttpBinResponse])
  val response = request.send(backend)

  response.body match {
    case Left(e)  => println(s"Got response exception:\n$e")
    case Right(r) => println(s"Origin's ip: ${r.origin}, header count: ${r.headers.size}")
  }
```

Similarly as above, since we've used the `asJson[HttpBinResponse]` response description, `response.body` is an 
`Either`. However here the left-side can indicate both an HTTP error, or a deserialization error. This is 
reflected in the type of the `response.body` value.

Alternatively, you can use the `asJsonOrFail` response description, so that in case of any error, an exception is
thrown.

## Adding logging

Logging can be added using the [logging backend wrapper](backends/wrappers/logging.md). For example, if you'd like to
use slf4j, you'll need the following dependency:

```
"com.softwaremill.sttp.client4" %% "slf4j-backend" % "4.0.17"
```

Then, you'll need to configure your backend:

```scala
import sttp.client4.*
import sttp.client4.logging.slf4j.Slf4jLoggingBackend

val backend = Slf4jLoggingBackend(DefaultSyncBackend())
```

Any requests sent using the backend will now be logged using slf4j!

## Even quicker

You can skip the step of creating a backend instance, by using `import sttp.client4.quick.*` instead of the usual `import sttp.client4.*`.
This brings into scope the same sttp API, and additionally a pre-configured synchronous backend instance, which can be used to send requests. 
This backend instance is global (created on first access), can't be customized and shouldn't be closed.

The `send()` extension method allows sending requests using that `backend` instance:

```scala
import sttp.client4.quick.*
quickRequest.get(uri"http://httpbin.org/ip").send()
```

Additionally, above we're using `quickRequest`, instead of `basicRequest`, to build the request description. 
`quickRequest` is pre-configured to always read HTTP responses as a `String`, regardless of the status code. 
You can read more about the initial request definitions [here](requests/basics.md).

## Next steps

Next, read on [how sttp client works](how.md) or see some [examples](examples.md).
