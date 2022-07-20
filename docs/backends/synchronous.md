# Synchronous backends

There are several synchronous backend implementations. Sending a request using these backends is a blocking operation, and results in a `sttp.client3.Response[T]`.

## Using HttpURLConnection

The default **synchronous** backend, available in the main jar for the JVM. 

To use, add an implicit value:

```scala mdoc:compile-only
import sttp.client3._
val backend = HttpURLConnectionBackend()
```

This backend works with all Scala versions. A Scala 3 build is available as well.

This backend supports host header override, but it has to be enabled by system property:
```
sun.net.http.allowRestrictedHeaders=true
```

## Using OkHttp

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "okhttp-backend" % "@VERSION@"
```

Create the backend using:

```scala mdoc:compile-only
import sttp.client3.okhttp.OkHttpSyncBackend

val backend = OkHttpSyncBackend()
```

or, if you'd like to instantiate the OkHttpClient yourself:

```scala mdoc:compile-only
import sttp.client3.okhttp.OkHttpSyncBackend
import okhttp3._

val okHttpClient: OkHttpClient = ???
val backend = OkHttpSyncBackend.usingClient(okHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Streaming

Synchronous backends don't support non-blocking [streaming](../requests/streaming.md).

## Websockets

Only the OkHttp backend supports regular [websockets](../websockets.md).
