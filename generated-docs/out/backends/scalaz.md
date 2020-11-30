# Scalaz backend

The [Scalaz](https://github.com/scalaz/scalaz) backend is **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `scalaz.concurrent.Task`. There's a transitive dependency on `scalaz-concurrent`.

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-scalaz" % "3.0.0-RC11"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to add an implicit value:

```scala
import sttp.client3._
import sttp.client3.asynchttpclient.scalaz.AsyncHttpClientScalazBackend

AsyncHttpClientScalazBackend().flatMap { backend => ??? }

// or, if you'd like to use custom configuration:
import org.asynchttpclient.AsyncHttpClientConfig
val config: AsyncHttpClientConfig = ???

AsyncHttpClientScalazBackend.usingConfig(config).flatMap { backend => ??? }

// or, if you'd like to use adjust the configuration sttp creates:
import org.asynchttpclient.DefaultAsyncHttpClientConfig
val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???

AsyncHttpClientScalazBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { backend => ??? }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
import org.asynchttpclient.AsyncHttpClient
val asyncHttpClient: AsyncHttpClient = ???

val backend = AsyncHttpClientScalazBackend.usingClient(asyncHttpClient)
```

## Streaming

This backend doesn't support non-blocking [streaming](../requests/streaming.md).

## Websockets

The backend doesn't support [websockets](../websockets.md).
