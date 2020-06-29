# Future-based backends

There are several backend implementations which are `scala.concurrent.Future`-based. These backends are **asynchronous**, sending a request is a non-blocking operation and results in a response wrapped in a `Future`. 

Apart from the ones described below, also the [Akka](akka.md) backend is `Future`-based.

```eval_rst
===================================== ================================================ ====================================
Class                                 Supported stream type                            Websocket support
===================================== ================================================ ====================================
``AkkaHttpBackend``                   ``akka.stream.scaladsl.Source[ByteString, Any]`` akka-streams
``AsyncHttpClientFutureBackend``      n/a                                              wrapping a low-level Java interface
``OkHttpFutureBackend``               n/a                                              wrapping a low-level Java interface
``HttpClientFutureBackend`` (Java11+) n/a                                              wrapping a low-level Java interface
===================================== ================================================ ====================================
```

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.2.2-SNAPSHOT"
```
And some imports:
```scala
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
```
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to add an implicit value:
```scala
implicit val sttpBackend = AsyncHttpClientFutureBackend()
```
or, if you'd like to use custom configuration:
```scala
import org.asynchttpclient.AsyncHttpClientConfig

val config: AsyncHttpClientConfig = ???
implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfig(config)
```
or, if you'd like to use adjust the configuration sttp creates:
```scala
import org.asynchttpclient.DefaultAsyncHttpClientConfig

val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfigBuilder(adjustFunction, sttpOptions)
```
or, if you'd like to instantiate the AsyncHttpClient yourself:
```scala
import org.asynchttpclient.AsyncHttpClient

val asyncHttpClient: AsyncHttpClient = ???  
implicit val sttpBackend = AsyncHttpClientFutureBackend.usingClient(asyncHttpClient)
```

## Using OkHttp

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "okhttp-backend" % "2.2.2-SNAPSHOT"
```
and some imports:
```scala
import sttp.client.okhttp.OkHttpFutureBackend
import scala.concurrent.ExecutionContext.Implicits.global
```

Create the backend using:
```scala
implicit val sttpBackend = OkHttpFutureBackend()
```
or, if you'd like to instantiate the OkHttpClient yourself:
```scala
import okhttp3.OkHttpClient

val asyncHttpClient: OkHttpClient = ???  
implicit val sttpBackend = OkHttpFutureBackend.usingClient(asyncHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "httpclient-backend" % "2.2.2-SNAPSHOT"
```
and some imports:
```scala
import sttp.client.httpclient.HttpClientFutureBackend
import scala.concurrent.ExecutionContext.Implicits.global
```

Create the backend using:

```scala
implicit val sttpBackend = HttpClientFutureBackend()
```
or, if you'd like to instantiate the HttpClient yourself:
```scala
import java.net.http.HttpClient

val client: HttpClient = ???  
implicit val sttpBackend = HttpClientFutureBackend.usingClient(client)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards, works with all Scala versions. A Dotty build is available as well.

## Streaming

The [Akka backend](akka.md) supports streaming using akka-streams.

Other backends don't support non-blocking [streaming](../requests/streaming.md).

## Websockets

The [Akka backend](akka.md) supports websockets through a high-level, streaming, akka-streams-based interface.

Other backends support websockets by wrapping the appropriate [low-level Java interface](../websockets.md).
