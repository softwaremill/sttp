![sttp](https://github.com/softwaremill/sttp/raw/master/banner.png)

[![Ideas, suggestions, problems, questions](https://img.shields.io/badge/Discourse-ask%20question-blue)](https://softwaremill.community/c/sttp-client)
[![CI](https://github.com/softwaremill/sttp/workflows/CI/badge.svg)](https://github.com/softwaremill/sttp/actions?query=workflow%3ACI+branch%3Amaster)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp.client4/core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp.client4/core_2.12)
[![Open in Gitpod](https://img.shields.io/badge/open%20in-gitpod-blue)](https://gitpod.io/#https://github.com/softwaremill/sttp)

# The Scala HTTP client that you always wanted!

> This is the development version of the upcoming sttp client 4. For the current stable version, see [sttp 3 on GitHub](https://github.com/softwaremill/sttp/tree/v3) and its [documentation](https://sttp.softwaremill.com/en/stable).

## Welcome!

[sttp client](https://github.com/softwaremill/sttp) is an open-source library which provides a clean, programmer-friendly API to describe HTTP
requests and how to handle responses. Requests are sent using one of the backends, which wrap lower-level Scala or Java HTTP client implementations. The backends can integrate with a variety of Scala stacks, providing both synchronous and asynchronous, procedural and functional interfaces.
 
Backend implementations include the HTTP client that is shipped with Java, as well as ones based on [akka-http](https://doc.akka.io/docs/akka-http/current/scala/http/), [http4s](https://http4s.org), [OkHttp](http://square.github.io/okhttp/). They integrate with [Akka](https://akka.io), [Monix](https://monix.io), [fs2](https://github.com/functional-streams-for-scala/fs2), [cats-effect](https://github.com/typelevel/cats-effect), [scalaz](https://github.com/scalaz/scalaz) and [ZIO](https://github.com/zio/zio). Supported Scala versions include 2.12, 2.13 and 3, Scala.JS and Scala Native; supported Java versions include 11+.

Here's a quick example of sttp client in action:
 
```scala
import sttp.client4._

val sort: Option[String] = None
val query = "http language:scala"

// the `query` parameter is automatically url-encoded
// `sort` is removed, as the value is not defined
val request = basicRequest.get(uri"https://api.github.com/search/repositories?q=$query&sort=$sort")
  
val backend = DefaultSyncBackend()
val response = request.send(backend)

// response.header(...): Option[String]
println(response.header("Content-Length")) 

// response.body: by default read into an Either[String, String] to indicate failure or success 
println(response.body)                                 
```

## Documentation

sttp (v4) documentation is available at [sttp.softwaremill.com/en/latest](https://sttp.softwaremill.com/en/latest).

sttp (v3) documentation is available at [sttp.softwaremill.com/en/stable](https://sttp.softwaremill.com/en/stable).

sttp (v2) documentation is available at [sttp.softwaremill.com/en/v2](https://sttp.softwaremill.com/en/v2).

sttp (v1) documentation is available at [sttp.softwaremill.com/en/v1](https://sttp.softwaremill.com/en/v1).

scaladoc is available at [https://www.javadoc.io](https://www.javadoc.io/doc/com.softwaremill.sttp.client4/core_2.12/4.0.0-M12)

## Quickstart with scala-cli

Add the following directive to the top of your scala file to add the core sttp dependency:
If you are using [scala-cli](https://scala-cli.virtuslab.org), you can quickly start experimenting with sttp by copy-pasting the following:

```
//> using dep "com.softwaremill.sttp.client4::core:4.0.0-M12"
import sttp.client4.quick._
quickRequest.get(uri"http://httpbin.org/ip").send()
```

The `quick` package import brings in the sttp API and a pre-configured, global synchronous backend instance.

## Quickstart with Ammonite

Similarly, using [Ammonite](http://ammonite.io):

```scala
import $ivy.`com.softwaremill.sttp.client4::core:4.0.0-M12`
import sttp.client4.quick._
quickRequest.get(uri"http://httpbin.org/ip").send()
```

## Quickstart with sbt

Add the following dependency:

```scala
"com.softwaremill.sttp.client4" %% "core" % "4.0.0-M12"
```

Then, import:

```scala
import sttp.client4._
```

Type `basicRequest.` and see where your IDE’s auto-complete gets you!

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* sttp client: this project
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)
* [sttp shared](https://github.com/softwaremill/sttp-shared): shared web socket, FP abstractions, capabilities and streaming code.
* [sttp apispec](https://github.com/softwaremill/sttp-apispec): OpenAPI, AsyncAPI and JSON Schema models.

## Contributing

If you have a question, suggestion, or hit a problem, feel free to ask on our [discourse forum](https://softwaremill.community/c/sttp-client)!

Or, if you encounter a bug, something is unclear in the code or documentation, don’t hesitate and open an issue on GitHub.

We are also always looking for contributions and new ideas, so if you’d like to get into the project, check out the [open issues](https://github.com/softwaremill/sttp/issues), or post your own suggestions!

Note that running the default `test` task will run the tests using both the JVM and JS backends, and is likely to run out of memory.
If you'd like to run the tests using *only* the JVM backend, execute: `sbt rootJVM/test`.

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

That is, in sbt run: `set version := "4.0.0-M12"`, before running `mdoc` in `docs`.

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

Copyright (C) 2017-2024 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
