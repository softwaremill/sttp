# sttp: the Scala HTTP client you always wanted!

Welcome!

sttp client is an open-source HTTP client for Scala, supporting various approaches to writing Scala code: synchronous (direct-style), `Future`-based, and using functional effect systems (cats-effect, ZIO, Monix, Kyo, scalaz).

The library is available for Scala 2.12, 2.13 and 3. Supported platforms are the JVM (Java 11+), Scala.JS and Scala Native.

Here's a quick example of sttp client in action, runnable using [scala-cli](https://scala-cli.virtuslab.org):

```scala
//> using dep com.softwaremill.sttp.client4::core::4.0.12

import sttp.client4.quick.*

@main def run(): Unit =
  println(quickRequest.get(uri"http://httpbin.org/ip").send())
```

sttp client addresses common HTTP client use cases, such as interacting with JSON APIs (with automatic serialization of request bodies and deserialization of response bodies), uploading and downloading files, submitting form data, handling multipart requests, and working with WebSockets.

The driving principle of sttp client's design is to provide a clean, programmer-friendly API to describe HTTP requests, along with response handling. This ensures that resources, such as HTTP connections, are used safely, also in the presence of errors.

sttp client integrates with a number of lower-level Scala and Java HTTP client implementations through backends (using Java's `HttpClient`, Akka HTTP, Pekko HTTP, http4s, OkHttp, Armeria), offering a wide range of choices when it comes to protocol support, connectivity settings and programming stack compatibility. 

Additionally, sttp client seamlessly integrates with popular libraries for JSON handling (e.g., circe, uPickle, jsoniter, json4s, play-json, ZIO Json), logging, metrics, and tracing (e.g., slf4j, scribe, OpenTelemetry, Prometheus). It also supports streaming libraries (e.g., fs2, ZIO Streams, Akka Streams, Pekko Streams) and provides tools for testing HTTP interactions.

Some more features: URI interpolation, a self-managed backend, and type-safe HTTP error/success representation, are demonstrated by the below example:

```scala
//> using dep com.softwaremill.sttp.client4::core::4.0.12

import sttp.client4.*

@main def sttpDemo(): Unit =
  val sort: Option[String] = None
  val query = "http language:scala"

  // the `query` parameter is automatically url-encoded
  // `sort` is removed, as the value is not defined
  val request = basicRequest.get(
    uri"https://api.github.com/search/repositories?q=$query&sort=$sort")

  val backend = DefaultSyncBackend()
  val response = request.send(backend)

  // response.header(...): Option[String]
  println(response.header("Content-Length")) 

  // since we're using basicRequest, the response.body is read into an 
  // Either[String, String] to indicate failure or success 
  println(response.body)
```

But that's just a small glimpse of sttp client's features! For more examples, see the [usage examples](examples.md) section. 

To start using sttp client in your project, see the [quickstart](quickstart.md). Or, browse the documentation to find the topics that interest you the most! ScalaDoc is available at [https://www.javadoc.io](https://www.javadoc.io/doc/com.softwaremill.sttp.client4/core_2.12/4.0.12).

sttp client is licensed under Apache2, the source code is [available on GitHub](https://github.com/softwaremill/sttp).

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* sttp client: this project
* [sttp tapir](https://github.com/softwaremill/tapir): rapid development of self-documenting APIs
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)
* [sttp shared](https://github.com/softwaremill/sttp-shared): shared web socket, FP abstractions, capabilities and streaming code.
* [sttp apispec](https://github.com/softwaremill/sttp-apispec): OpenAPI, AsyncAPI and JSON Schema models.
* [sttp openai](https://github.com/softwaremill/sttp-openai): Scala client wrapper for OpenAI and OpenAI-compatible APIs. Use the power of ChatGPT inside your code!

Third party projects:

* [sttp-oauth2](https://github.com/polyvariant/sttp-oauth2): OAuth2 client library for Scala

## Try sttp client in your browser!

[Check out & play with a simple example on Scastie!](https://scastie.scala-lang.org/adamw/aOf32MZsTPesobwfWG5nDQ)

# Table of contents

```{eval-rst}
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart
   how
   support
   goals
   community

.. toctree::
   :maxdepth: 2
   :caption: How-to's
   
   examples
   migrate_v3_v4

.. toctree::
   :maxdepth: 2
   :caption: HTTP model

   model/model
   model/uri

.. toctree::
   :maxdepth: 2
   :caption: Request definition

   requests/basics
   requests/headers
   requests/cookies
   requests/authentication
   requests/body
   requests/multipart
   requests/streaming
   requests/type

.. toctree::
   :maxdepth: 2
   :caption: Responses

   responses/basics
   responses/body
   responses/exceptions

.. toctree::
   :maxdepth: 2
   :caption: Other topics

   other/websockets
   other/json
   other/xml
   other/resilience
   other/openapi
   other/sse
   other/body_callbacks

.. toctree::
   :maxdepth: 2
   :caption: Backends

   backends/summary
   backends/start_stop
   backends/synchronous
   backends/akka
   backends/pekko
   backends/future
   backends/monix
   backends/catseffect
   backends/fs2
   backends/scalaz
   backends/zio
   backends/http4s
   backends/finagle
   backends/javascript/fetch
   backends/native/curl

.. toctree::
   :maxdepth: 2
   :caption: Backend wrappers

   backends/wrappers/opentelemetry
   backends/wrappers/prometheus
   backends/wrappers/logging
   backends/wrappers/cache
   backends/wrappers/custom

.. toctree::
   :maxdepth: 2
   :caption: Testing

   testing/stub
   testing/curl

.. toctree::
   :maxdepth: 2
   :caption: Configuration

   conf/timeouts
   conf/ssl
   conf/proxy
   conf/redirects

.. toctree::
   :maxdepth: 2
   :caption: More information

   other
```
