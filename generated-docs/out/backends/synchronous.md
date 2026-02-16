# Synchronous backends

There are several synchronous backend implementations. Sending a request using these backends is a blocking operation, and results in a `sttp.client4.Response[T]`.

## Using HttpClient

The default **synchronous** backend. To use, you don't need any extra dependencies, `core` is enough:

```
"com.softwaremill.sttp.client4" %% "core" % "4.0.18"
```

Create the backend using:

```scala
import sttp.client4.httpclient.HttpClientSyncBackend

val backend = HttpClientSyncBackend()
```

or, if you'd like to instantiate the HttpClient yourself:

```scala
import sttp.client4.httpclient.HttpClientSyncBackend
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientSyncBackend.usingClient(httpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:

```
-Djdk.httpclient.allowRestrictedHeaders=host
```

## Using HttpURLConnection

To use, you don't need any extra dependencies, `core` is enough: 

```
"com.softwaremill.sttp.client4" %% "core" % "4.0.18"
```

Create the backend using:

```scala
import sttp.client4.httpurlconnection.HttpURLConnectionBackend

val backend = HttpURLConnectionBackend()
```

This backend supports host header override, but it has to be enabled by system property:

```
-Dsun.net.http.allowRestrictedHeaders=true
```

## Using OkHttp

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "okhttp-backend" % "4.0.18"
```

Create the backend using:

```scala
import sttp.client4.okhttp.OkHttpSyncBackend

val backend = OkHttpSyncBackend()
```

or, if you'd like to instantiate the OkHttpClient yourself:

```scala
import sttp.client4.okhttp.OkHttpSyncBackend
import okhttp3.*

val okHttpClient: OkHttpClient = ???
val backend = OkHttpSyncBackend.usingClient(okHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Streaming

Synchronous backends don't support non-blocking [streaming](../requests/streaming.md). However, blocking, synchronous 
streaming is supported when sttp is used in combination with [Ox](https://ox.softwaremill.com): a Scala 3 toolkit that
allows you to handle concurrency and resiliency in direct-style, leveraging Java 21+ virtual threads.

For a start, in a virtual thread environment, often using `InputStreams` is enough. However, for more flexibility, 
these can be created or consumed using Ox's `Flow`s.

See the [example](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client4/examples/streaming/streamOx.scala)
where sending & receiving bodies describes as `Flow`s is demonstrated.

## Websockets

Both HttpClient and OkHttp backends support regular [WebSockets](../other/websockets.md), with a blocking, synchronous
streaming variant available through integration with Ox. See the linked WebSockets docs for details.

## Server-sent events

If you're using Ox with `sttp`, you can handle SSE as a `Flow[ServerSentEvent]`:

```
// sbt dependency
"com.softwaremill.sttp.client4" %% "ox" % "4.0.18",
```

```scala
import sttp.client4.*
import sttp.client4.impl.ox.sse.OxServerSentEvents
import java.io.InputStream

def handleSse(is: InputStream): Unit =
  OxServerSentEvents.parse(is).runForeach(event => println(s"Received event: $event"))

val backend = DefaultSyncBackend()
  basicRequest
    .get(uri"https://postman-echo.com/server-events/3")
    .response(asInputStreamAlways(handleSse))
    .send(backend)
```
