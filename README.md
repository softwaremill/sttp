![sttp](https://github.com/softwaremill/sttp/raw/master/banner.png)

[![Ideas, suggestions, problems, questions](https://img.shields.io/badge/Discourse-ask%20question-blue)](https://softwaremill.community/c/sttp-client)
[![CI](https://github.com/softwaremill/sttp/workflows/CI/badge.svg)](https://github.com/softwaremill/sttp/actions?query=workflow%3ACI+branch%3Amaster)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp.client4/core_3/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp.client4/core_3)

# The Scala HTTP client you always wanted!

## Welcome!

sttp client is an open-source HTTP client for Scala, supporting various approaches to writing Scala code: synchronous (direct-style), `Future`-based, and using functional effect systems (cats-effect, ZIO, Monix, Kyo, scalaz).

The library is available for Scala 2.12, 2.13 and 3. Supported platforms are the JVM (Java 11+), Scala.JS and Scala Native.

Here's a quick example of sttp client in action, runnable using [scala-cli](https://scala-cli.virtuslab.org):

```scala
//> using dep com.softwaremill.sttp.client4::core:4.0.0

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
//> using dep com.softwaremill.sttp.client4::core:4.0.0

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

  // response.body: read into an Either[String, String] to indicate failure or success 
  println(response.body)
```

But that's just a small glimpse of sttp client's features! For more examples, see the [usage examples](https://sttp.softwaremill.com/en/latest/examples.html). 

## Documentation

sttp (v4) documentation is available at [sttp.softwaremill.com](https://sttp.softwaremill.com).

sttp (v3) documentation is available at [sttp.softwaremill.com/en/v3](https://sttp.softwaremill.com/en/v3).

sttp (v2) documentation is available at [sttp.softwaremill.com/en/v2](https://sttp.softwaremill.com/en/v2).

sttp (v1) documentation is available at [sttp.softwaremill.com/en/v1](https://sttp.softwaremill.com/en/v1).

scaladoc is available at [https://www.javadoc.io](https://www.javadoc.io/doc/com.softwaremill.sttp.client4/core_3/4.0.0)

## Quickstart with scala-cli

Add the following directive to the top of your scala file to add the core sttp dependency:
If you are using [scala-cli](https://scala-cli.virtuslab.org), you can quickly start experimenting with sttp by copy-pasting the following:

```
//> using dep "com.softwaremill.sttp.client4::core:4.0.0"
import sttp.client4.quick.*
quickRequest.get(uri"http://httpbin.org/ip").send()
```

The `quick` package import brings in the sttp API and a pre-configured, global synchronous backend instance.

## Quickstart with Ammonite

Similarly, using [Ammonite](http://ammonite.io):

```scala
import $ivy.`com.softwaremill.sttp.client4::core:4.0.0`
import sttp.client4.quick.*
quickRequest.get(uri"http://httpbin.org/ip").send()
```

## Quickstart with sbt

Add the following dependency:

```scala
"com.softwaremill.sttp.client4" %% "core" % "4.0.0"
```

Then, import:

```scala
import sttp.client4.*
```

Type `basicRequest.` and see where your IDE’s auto-complete gets you!

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* sttp client: this project
* [sttp tapir](https://github.com/softwaremill/tapir): rapid development of self-documenting APIs
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)
* [sttp shared](https://github.com/softwaremill/sttp-shared): shared web socket, FP abstractions, capabilities and streaming code.
* [sttp apispec](https://github.com/softwaremill/sttp-apispec): OpenAPI, AsyncAPI and JSON Schema models.
* [sttp openai](https://github.com/softwaremill/sttp-openai): Scala client wrapper for OpenAI and OpenAI-compatible APIs. Use the power of ChatGPT inside your code!

## Contributing

If you have a question, suggestion, or hit a problem, feel free to ask on our [discourse forum](https://softwaremill.community/c/sttp-client)!

Or, if you encounter a bug, something is unclear in the code or documentation, don’t hesitate and open an issue on GitHub.

We are also always looking for contributions and new ideas, so if you’d like to get into the project, check out the [open issues](https://github.com/softwaremill/sttp/issues), or post your own suggestions!

Note that running the default `test` task will run the tests using both the JVM and JS backends, and is likely to run out of memory.
If you'd like to run the tests using *only* the JVM backend, execute: `sbt rootJVM/test`.

When you have a PR ready, take a look at our ["How to prepare a good PR" guide](https://softwaremill.community/t/how-to-prepare-a-good-pr-to-a-library/448). Thanks! :)

### Importing into IntelliJ

By default, when importing to IntelliJ or Metals, only the Scala 2.13/JVM subprojects will be imported. This is controlled by the `ideSkipProject` setting in `build.sbt` (inside `commonSettings`).

If you'd like to work on a different platform or Scala version, simply change this setting temporarily so that the correct subprojects are imported. For example:

```
// import only Scala 2.13, JS projects
ideSkipProject := (scalaVersion.value != scala2_13) || !thisProjectRef.value.project.contains("JS")

// import only Scala 3, JVM projects
ideSkipProject := (scalaVersion.value != scala3) || thisProjectRef.value.project.contains("JS") || thisProjectRef.value.project.contains("Native"),

// import only Scala 2.13, Native projects
ideSkipProject := (scalaVersion.value != scala2_13) || !thisProjectRef.value.project.contains("Native")
```

### Modifying documentation

The documentation is typechecked using [mdoc](https://scalameta.org/mdoc/). The sources for the documentation exist in `docs`. Don't modify the generated documentation in `generated-docs`, as these files will get overwritten!

When generating documentation, it's best to set the version to the current one, so that the generated doc files don't include modifications with the current snapshot version. 

That is, in sbt run: `set ThisBuild/version := "4.0.0"`, before running `mdoc` in `docs`.

### Testing the Scala.JS backend

In order to run tests against JS backend you will need to install [Google Chrome](https://www.google.com/chrome/).

### Building & testing the scala-native backend

By default, sttp-native will **not** be included in the aggregate build of the root project. To include it, define the `STTP_NATIVE` environmental variable before running sbt, e.g.:

```
STTP_NATIVE=1 sbt
```

You might need to install some additional libraries, see the [scala native](http://www.scala-native.org/en/latest/user/setup.html) documentation site. On macos, you might additionally need:

```
ln -s /usr/local/opt/openssl/lib/libcrypto.dylib /usr/local/lib/
ln -s /usr/local/opt/openssl/lib/libssl.dylib /usr/local/lib/
```

## Commercial Support

We offer commercial support for sttp and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

## Copyright

Copyright (C) 2017-2025 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
