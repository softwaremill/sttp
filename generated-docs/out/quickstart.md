# Quickstart

The core sttp client API comes in a single jar, with a transitive dependency on [sttp model](https://github.com/softwaremill/sttp-model). This also includes a default [synchronous simple client](simple_sync.md) and [synchronous](backends/synchronous.md) and [`Future`-based] backends, based on Java's `HttpClient`.

To integrate with other parts of your application and various effect systems, you'll often need to use an alternate backend (but what's important is that the API remains the same!). See the section on [backends](backends/summary.md) for a short guide on which backend to choose, and a list of all implementations.

`sttp client` is available for Scala 2.11, 2.12 and 2.13, as well as for Scala 3 and requires Java 11 or higher.

`sttp client` is also available for Scala.js 1.0 and Scala Native. Note that not all modules are compatible with these
platforms, and that each has its own dedicated set of backends.

## Using sbt

The basic dependency which provides the API, together with a synchronous and `Future`-based backends, is:

```scala
"com.softwaremill.sttp.client3" %% "core" % "3.8.3"
```

## Simple synchronous client

If you'd like to send some requests synchronously, take a look at the [simple synchronous client](simple_sync.md).

## Using Ammonite

If you are an [Ammonite](https://ammonite.io) user, you can quickly start experimenting with sttp by copy-pasting the following:

```scala
import $ivy.`com.softwaremill.sttp.client3::core:3.8.3`
import sttp.client3.quick._
simpleSttpClient.send(quickRequest.get(uri"http://httpbin.org/ip"))
```

Importing the `quick` object has the same effect as importing `sttp.client3._`, plus defining a synchronous backend (`val backend = HttpClientSyncBackend()`), so that sttp can be used right away.

If the default backend is for some reason insufficient, you can also use one based on OkHttp:

```scala
import $ivy.`com.softwaremill.sttp.client3::okhttp-backend:3.8.3`
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

val backend = HttpClientSyncBackend()
val response = basicRequest
  .body("Hello, world!")  
  .post(uri"https://httpbin.org/post?hello=world").send(backend)

println(response.body)            
```

Next, read on [how sttp client works](how.md) or see some [examples](examples.md).
