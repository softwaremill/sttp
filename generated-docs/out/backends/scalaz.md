# Scalaz backend

The [Scalaz](https://github.com/scalaz/scalaz) backend is **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `scalaz.concurrent.Task`. There's a transitive dependency on `scalaz-concurrent`.

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-scalaz" % "2.2.0"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to add an implicit value:

```scala
import sttp.client._
import sttp.client.asynchttpclient.scalaz.AsyncHttpClientScalazBackend

AsyncHttpClientScalazBackend().flatMap { implicit backend => ??? }

// or, if you'd like to use custom configuration:
import org.asynchttpclient.AsyncHttpClientConfig
val config: AsyncHttpClientConfig = ???

AsyncHttpClientScalazBackend.usingConfig(config).flatMap { implicit backend => ??? }

// or, if you'd like to use adjust the configuration sttp creates:
import org.asynchttpclient.DefaultAsyncHttpClientConfig
val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???

AsyncHttpClientScalazBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { implicit backend => ??? }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
import org.asynchttpclient.AsyncHttpClient
val asyncHttpClient: AsyncHttpClient = ???

implicit val sttpBackend = AsyncHttpClientScalazBackend.usingClient(asyncHttpClient)
```

## Streaming

This backend doesn't support non-blocking [streaming](../requests/streaming.md).

## Websockets

The backend supports websockets by wrapping a [low-level Java interface](../websockets.md), `sttp.client.asynchttpclient.WebSocketHandler`.
