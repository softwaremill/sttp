# Synchronous backends

There are several synchronous backend implementations. Sending a request using these backends is a blocking operation, and results in a `sttp.client3.Response[T]`.

## Using HttpClient

The default **synchronous** backend. To use, you don't need any extra dependencies, `core` is enough:

```
"com.softwaremill.sttp.client3" %% "core" % "3.8.0"
```

Create the backend using:

```scala
import sttp.client3.HttpClientSyncBackend

val backend = HttpClientSyncBackend()
```

or, if you'd like to instantiate the HttpClient yourself:

```scala
import sttp.client3.HttpClientSyncBackend
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
"com.softwaremill.sttp.client3" %% "core" % "3.8.0"
```

Create the backend using:

```scala
import sttp.client3.HttpURLConnectionBackend

val backend = HttpURLConnectionBackend()
```

This backend supports host header override, but it has to be enabled by system property:

```
-Dsun.net.http.allowRestrictedHeaders=true
```

## Using OkHttp

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "okhttp-backend" % "3.8.0"
```

Create the backend using:

```scala
import sttp.client3.okhttp.OkHttpSyncBackend

val backend = OkHttpSyncBackend()
```

or, if you'd like to instantiate the OkHttpClient yourself:

```scala
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
