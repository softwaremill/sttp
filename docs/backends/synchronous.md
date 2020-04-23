# Synchronous backends

There are several synchronous backend implementations. Sending a request using these backends is a blocking operation, and results in a `sttp.client.Response[T]`.

## Using HttpURLConnection

The default **synchronous** backend, available in the main jar for the JVM. 

To use, add an implicit value:

```scala
implicit val sttpBackend = HttpURLConnectionBackend()
```

## Using OkHttp

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "okhttp-backend" % "2.1.0-RC1"
```

Create the backend using:

```scala
import sttp.client.okhttp.OkHttpSyncBackend

implicit val sttpBackend = OkHttpSyncBackend()

// or, if you'd like to instantiate the OkHttpClient yourself:
implicit val sttpBackend = OkHttpSyncBackend.usingClient(okHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "httpclient-backend" % "2.1.0-RC1"
```

Create the backend using:

```scala
import sttp.client.httpclient.HttpClientSyncBackend

implicit val sttpBackend = HttpClientSyncBackend()

// or, if you'd like to instantiate the HttpClient yourself:
implicit val sttpBackend = HttpClientSyncBackend.usingClient(httpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

## Streaming

Synchronous backends don't support non-blocking [streaming](../requests/streaming.html).

## Websockets

The `HttpURLConnection`-based backend doesn't support websockets.

OkHttp and HttpClient backends support websockets by wrapping a [low-level Java interface](../websockets.html):
 
* `sttp.client.okhttp.WebSocketHandler`, or
* `sttp.client.httpclient.WebSocketHandler`
