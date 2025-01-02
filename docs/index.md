# sttp: the Scala HTTP client you always wanted!

> This is the development version of the upcoming sttp client 4. For the current stable version, see [sttp 3 on GitHub](https://github.com/softwaremill/sttp/tree/v3) and its [documentation](https://sttp.softwaremill.com/en/stable).

Welcome!

sttp client is an open-source HTTP client for Scala, supporting various approaches to writing Scala code: synchronous (direct-style), `Future`-based, and using functional effect systems (cats-effect, ZIO, Monix, Kyo, scalaz).

The library is available for Scala 2.12, 2.13 and 3. Supported platforms are the JVM (Java 11+), Scala.JS and Scala Native.

sttp client addresses common HTTP client use cases, such as interacting with JSON APIs (with automatic serialization of request bodies and deserialization of response bodies), uploading and downloading files, submitting form data, handling multi-part requests, and working with WebSockets.

The driving principle of sttp client's design is to provide a clean, programmer-friendly API to describe HTTP requests, along with response handling. This ensures that resources, such as HTTP connections, are used safely, also in the presence of errors.

sttp client integrates with a number of lower-level Scala and Java HTTP client implementations through backends (using Java's `HttpClient`, akka-http, pekko-http, http4s, OkHttp, Armeria), offering a wide range of choices when it comes to protocol support, connectivity settings and programming stack compatibility. 

Additionally, sttp client seamlessly integrates with popular libraries for JSON handling (e.g., circe, uPickle, jsoniter, json4s, play-json, ZIO Json), logging, metrics, and tracing (e.g., slf4j, scribe, OpenTelemetry, Prometheus). It also supports streaming libraries (e.g., fs2, ZIO Streams, Akka Streams, Pekko Streams) and provides tools for testing HTTP interactions.

Here's a quick example of sttp client in action, runnable using [scala-cli](https://scala-cli.virtuslab.org):

```scala
//> using dep com.softwaremill.sttp.client4::core:4.0.0-M20

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

  // response.body: by default read into an Either[String, String] to indicate failure or success 
  println(response.body)
```

For more examples, see the [usage examples](examples.md) section. To start using sttp client in your project, see the [quickstart](quickstart.md). Or, browse the documentation to find the topics that interest you the most! ScalaDoc is available at [https://www.javadoc.io](https://www.javadoc.io/doc/com.softwaremill.sttp.client4/core_2.12/4.0.0-M9).

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* sttp client: this project
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)
* [sttp shared](https://github.com/softwaremill/sttp-shared): shared web socket, FP abstractions, capabilities and streaming code.
* [sttp apispec](https://github.com/softwaremill/sttp-apispec): OpenAPI, AsyncAPI and JSON Schema models.

Third party projects:

* [sttp-oauth2](https://github.com/ocadotechnology/sttp-oauth2): OAuth2 client library for Scala
* [sttp openai](https://github.com/softwaremill/sttp-openai): Scala client wrapper for OpenAI (and OpenAI-compatible) API. Use the power of ChatGPT inside your code!

## Try sttp client in your browser!

[Check out & play with a simple example on Scastie!](https://scastie.scala-lang.org/adamw/aOf32MZsTPesobwfWG5nDQ)

## Sponsors

Development and maintenance of sttp client is sponsored by [SoftwareMill](https://softwaremill.com), a software development and consulting company. We help clients scale their business through software. Our areas of expertise include backends, distributed systems, machine learning, platform engineering and data analytics.

[![](https://files.softwaremill.com/logo/logo.png "SoftwareMill")](https://softwaremill.com)

## Commercial Support

We offer commercial support for sttp and related technologies, as well as development services. [Contact us](https://softwaremill.com/contact/) to learn more about our offer!

# Table of contents

```{eval-rst}
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart
   how
   goals
   community

.. toctree::
   :maxdepth: 2
   :caption: How-to's
   
   examples

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

   websockets
   json
   xml
   resilience
   openapi

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
   backends/wrappers/custom

.. toctree::
   :maxdepth: 2
   :caption: Testing

   testing

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
