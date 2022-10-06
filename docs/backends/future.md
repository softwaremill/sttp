# Future-based backends

There are several backend implementations which are `scala.concurrent.Future`-based. These backends are **asynchronous**, sending a request is a non-blocking operation and results in a response wrapped in a `Future`. 

Apart from the ones described below, also the [Akka](akka.md) backend is `Future`-based.

```eval_rst
===================================== ================================================ ==========================
Class                                 Supported stream type                            Websocket support
===================================== ================================================ ==========================
``HttpClientFutureBackend``           n/a                                              yes (regular)
``AkkaHttpBackend``                   ``akka.stream.scaladsl.Source[ByteString, Any]`` yes (regular & streaming)
``OkHttpFutureBackend``               n/a                                              yes (regular)
``ArmeriaFutureBackend``              n/a                                              n/a
===================================== ================================================ ==========================
```

## Using HttpClient

To use, you don't need any extra dependencies, `core` is enough:

```
"com.softwaremill.sttp.client3" %% "core" % "@VERSION@"
```

You'll need the following imports:

```scala mdoc:reset:silent
import sttp.client3.HttpClientFutureBackend
import scala.concurrent.ExecutionContext.Implicits.global
```

Create the backend using:

```scala mdoc:compile-only
val backend = HttpClientFutureBackend()
```

or, if you'd like to instantiate the HttpClient yourself:

```scala mdoc:compile-only
import java.net.http.HttpClient

val client: HttpClient = ??? 
val backend = HttpClientFutureBackend.usingClient(client)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards. 

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:

```
-Djdk.httpclient.allowRestrictedHeaders=host
```


## Using OkHttp

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "okhttp-backend" % "@VERSION@"
```

and some imports:

```scala mdoc:reset:silent
import sttp.client3.okhttp.OkHttpFutureBackend
import scala.concurrent.ExecutionContext.Implicits.global
```

Create the backend using:

```scala mdoc:compile-only
val backend = OkHttpFutureBackend()
```

or, if you'd like to instantiate the OkHttpClient yourself:

```scala mdoc:compile-only
import okhttp3.OkHttpClient

val okHttpClient: OkHttpClient = ??? 
val backend = OkHttpFutureBackend.usingClient(okHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Using Armeria

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "armeria-backend" % "@VERSION@"
```

add imports:

```scala mdoc:silent
import sttp.client3.armeria.future.ArmeriaFutureBackend
```

create client:

```scala mdoc:compile-only
val backend = ArmeriaFutureBackend()

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaFutureBackend.usingDefaultClient()
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself::

```scala mdoc:compile-only
import com.linecorp.armeria.client.circuitbreaker._
import com.linecorp.armeria.client.WebClient

// Fluently build Armeria WebClient with built-in decorators
val client = WebClient.builder("https://my-service.com")
             // Open circuit on 5xx server error status
             .decorator(CircuitBreakerClient.newDecorator(CircuitBreaker.ofDefaultName(),
               CircuitBreakerRule.onServerErrorStatus()))
             .build()
             
val backend = ArmeriaFutureBackend.usingClient(client)
```

```eval_rst
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http) and doesn't support host header override.
Armeria's [ClientFactory](https://armeria.dev/docs/client-factory) manages connections and protocol-specific properties.
Please visit [the official documentation](https://armeria.dev/docs/client-factory) to learn how to configure it.

## Streaming

The [Akka backend](akka.md) supports streaming using akka-streams.

Other backends don't support non-blocking [streaming](../requests/streaming.md).

## Websockets

Some of the backends (see above) support regular and streaming [websockets](../websockets.md).
