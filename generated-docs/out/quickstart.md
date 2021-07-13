# Quickstart

The core sttp client API comes in a single jar, with a transitive dependency on [sttp model](https://github.com/softwaremill/sttp-model). This also includes a default, [synchronous](backends/synchronous.md) backend, which is based on Java's `HttpURLConnection`. 

To integrate with other parts of your application, you'll often need to use an alternate backend (but what's important is that the API remains the same!). See the section on [backends](backends/summary.md) for a short guide on which backend to choose, and a list of all implementations.

## Using sbt

The basic dependency which provides the API and the default synchronous backend is:

```scala
"com.softwaremill.sttp.client3" %% "core" % "3.3.10"
```

`sttp client` is available for Scala 2.11, 2.12 and 2.13, and requires Java 8, as well as for Scala 3.

`sttp client` is also available for Scala.js 1.0. Note that not all modules are compatible and there are no backends that can be used on both. The last version compatible with Scala.js 0.6 was 2.2.1. Scala Native is supported as well.

## Using Ammonite

If you are an [Ammonite](https://ammonite.io) user, you can quickly start experimenting with sttp by copy-pasting the following:

```scala
import $ivy.`com.softwaremill.sttp.client3::core:3.3.10`
import sttp.client3.quick._
quickRequest.get(uri"http://httpbin.org/ip").send(backend)
```

Importing the `quick` object has the same effect as importing `sttp.client3._`, plus defining a synchronous backend (`implict val backend = HttpURLConnectionBackend()`), so that sttp can be used right away.

If the default `HttpURLConnectionBackend` for some reason is insufficient, you can also use one based on OkHttp or HttpClient:

```scala
import $ivy.`com.softwaremill.sttp.client3::okhttp-backend:3.3.10`
import sttp.client3.okhttp.quick._
quickRequest.get(uri"http://httpbin.org/ip").send(backend)
```

## Imports

Working with sttp is most convenient if you import the `sttp.client3` package entirely:

```scala
import sttp.client3._
```

This brings into scope the starting point for defining requests and some helper methods. All examples in this guide assume that this import is in place.

And that's all you need to start using sttp client! To create and send your first request, import the above, type `basicRequest.` and see where your IDE's auto-complete gets you! Here's a simple request, using the synchronous backend:

```scala
import sttp.client3._

val backend = HttpURLConnectionBackend()
val response = basicRequest
  .body("Hello, world!")  
  .post(uri"https://httpbin.org/post?hello=world").send(backend)

println(response.body)            
```

Next, read on about the [how sttp client works](how.md) or see some [examples](examples.md).
