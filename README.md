# sttp

[![Join the chat at https://gitter.im/softwaremill/sttp](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/softwaremill/sttp?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/softwaremill/sttp.svg?branch=master)](https://travis-ci.org/softwaremill/sttp)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp/core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp/core_2.12)
[![Dependencies](https://app.updateimpact.com/badge/634276070333485056/sttp.svg?config=compile)](https://app.updateimpact.com/latest/634276070333485056/sttp)

The HTTP client for Scala that you always wanted!
 
```scala
import com.softwaremill.sttp._

val sort: Option[String] = None
val query = "http language:scala"

// the `query` parameter is automatically url-encoded
// `sort` is removed, as the value is not defined
val request = sttp.get(uri"https://api.github.com/search/repositories?q=$query&sort=$sort")
  
implicit val handler = HttpURLConnectionSttpHandler
val response = request.send()

// response.header(...): Option[String]
println(response.header("Content-Length")) 

// response.body: by default read into a String 
println(response.body)                     
```
 
## Goals of the project

* provide a simple, discoverable, no-surprises, reasonably type-safe API for 
making HTTP requests and reading responses
* separate definition of a request from request execution
* provide immutable, easily modifiable data structures for requests and 
responses
* support multiple execution backends, both synchronous and asynchronous
* provide support for backend-specific request/response streaming
* minimum dependencies

See also the [introduction to sttp](https://softwaremill.com/introducing-sttp-the-scala-http-client)
and [sttp streaming & URI interpolators](https://softwaremill.com/sttp-streaming-uri-interpolator) 
blogs.

## How is sttp different from other libraries?

* immutable request builder which doesn't impose any order in which request 
parameters need to be specified. Such an approach allows defining partial 
requests with common cookies/headers/options, which can later be specialized 
using a specific URI and HTTP method.
* support for multiple backends, both synchronous and asynchronous, with 
backend-specific streaming support
* URI interpolator with context-aware escaping, optional parameters support
and parameter collections

## Quickstart with Ammonite

If you are an [Ammonite](http://ammonite.io) user, you can quickly start 
experimenting with sttp by copy-pasting the following:

```scala
import $ivy.`com.softwaremill.sttp::core:0.0.6`
import com.softwaremill.sttp._
implicit val handler = HttpURLConnectionSttpHandler
sttp.get(uri"http://httpbin.org/ip").send()
```

## Adding sttp to your project 

SBT dependency:

```scala
"com.softwaremill.sttp" %% "core" % "0.0.6"
```

`sttp` is available for Scala 2.11 and 2.12, and requires Java 8. The core 
module has no transitive dependencies.

If you'd like to use an alternate backend, [see below](#supported-backends) 
for additional instructions.

## API

First, import:

```scala
import com.softwaremill.sttp._
```

This brings into scope `sttp`, the starting request (it's an empty request
with the `Accept-Encoding: gzip, defalte` header added). This request can 
be customised, each time yielding a new, immutable request description 
(unless a mutable body is set on the request, such as a byte array).

For example, we can set a cookie, string-body and specify that this should
be a `POST` request to a given URI:

```scala
val request = sttp
    .cookie("login", "me")
    .body("This is a test")
    .post(uri"http://endpoint.com/secret")
```

The request parameters (headers, cookies, body etc.) can be specified in any
order. There's a lot of ways in which you can customize a request: just
explore the API. And [more will be added](#todo)!

You can create a request description without knowing how it will be sent.
But to send a request, you will need a backend. A default, synchronous backend
based on Java's `HttpURLConnection` is provided out-of-the box. An implicit 
value of type `SttpHandler` needs to be in scope to invoke the `send()` on the
request: 

```scala
implicit val handler = HttpURLConnectionSttpHandler

val response: Response[String] = request.send()
```

By default the response body is read into a utf-8 string. How the response body
is handled is also part of the request description. The body can be ignore
(`.response(ignore)`), read into a sequence of parameters 
(`.response(asParams)`), mapped (`.mapResponse`) and more; some backends also 
support request & response streaming.

The default handler doesn't wrap the response into any container, but other
asynchronous handlers might do so. The type parameter in the `Response[_]`
type specifies the type of the body.

## URI interpolator

Using the URI interpolator it's possible to conveniently create `Uri` 
instances, which can then be used to specify request endpoints, for example:

```scala
import com.softwaremill.sttp._

val user = "Mary Smith"
val filter = "programming languages"

val endpoint: Uri = uri"http://example.com/$user/skills?filter=$filter"
```

Any values embedded in the URI will be URL-encoded, taking into account the 
context (e.g., the whitespace in `user` will be %-encoded as `%20D`, while the
whitespace in `filter` will be query-encoded as `+`). 

The possibilities of the interpolator don't end here. Other supported features:

* parameters can have optional values: if the value of a parameter is `None`, 
it will be removed
* maps, sequences of tuples and sequences of values can be embedded in the query 
part. They will be expanded into query parameters. Maps and sequences of tuples 
can also contain optional values, for which mappings will be removed 
if `None`.
* optional values in the host part will be expanded to a subdomain if `Some`, 
removed if `None`
* sequences in the host part will be expanded to a subdomain sequence
* if a string containing the protocol is embedded *as the very beginning*, it will 
not be escaped, allowing to embed entire addresses as prefixes, e.g.: 
`uri"$endpoint/login"`, where `val endpoint = "http://example.com/api"`.
 
A fully-featured example:

```scala
import com.softwaremill.sttp._
val secure = true
val scheme = if (secure) "https" else "http"
val subdomains = List("sub1", "sub2")
val vx = Some("y z")
val params = Map("a" -> 1, "b" -> 2)
val jumpTo = Some("section2")
uri"$scheme://$subdomains.example.com?x=$vx&$params#$jumpTo"

// generates:
// https://sub1.sub2.example.com?x=y+z&a=1&b=2#section2
```

## Supported backends

### Summary

| Class | Result wrapper | Supported stream type |
| --- | --- | --- |
| `HttpURLConnectionSttpHandler` | None (`Id`) | - |
| `AkkaHttpSttpHandler` | `scala.concurrent.Future` | `akka.stream.scaladsl.Source[ByteString, Any]` |
| `FutureAsyncHttpClientHandler` | `scala.concurrent.Future` | - |
| `ScalazAsyncHttpClientHandler` | `scalaz.concurrent.Task` | - |
| `MonixAsyncHttpClientHandler` | `monix.eval.Task` | `monix.reactive.Observable[ByteBuffer]` | 
| `CatsAsyncHttpClientHandler` | `F[_]: cats.effect.Async` | - | 
| `OkHttpSyncClientHandler` | None (`Id`) | - | 
| `OkHttpFutureClientHandler` | `scala.concurrent.Future` | - |
| `OkHttpMonixClientHandler` | `monix.eval.Task` | `monix.reactive.Observable[ByteBuffer]` |

### `HttpURLConnectionSttpHandler`

The default **synchronous** handler. Sending a request returns a response wrapped 
in the identity type constructor, which is equivalent to no wrapper at all.
 
To use, add an implicit value:

```scala
implicit val sttpHandler = HttpURLConnectionSttpHandler
```

### `AkkaHttpSttpHandler`

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp" %% "akka-http-handler" % "0.0.6"
```

This handler depends on [akka-http](http://doc.akka.io/docs/akka-http/current/scala/http/).
A fully **asynchronous** handler. Sending a request returns a response wrapped
in a `Future`.

Next you'll need to add an implicit value:

```scala
implicit val sttpHandler = AkkaHttpSttpHandler()

// or, if you'd like to use an existing actor system:
implicit val sttpHandler = AkkaHttpSttpHandler.usingActorSystem(actorSystem)
```

This backend supports sending and receiving 
[akka-streams](http://doc.akka.io/docs/akka/current/scala/stream/index.html)
streams of type `akka.stream.scaladsl.Source[ByteString, Any]`.

To set the request body as a stream:

```scala
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp._

import akka.stream.scaladsl.Source
import akka.util.ByteString

val source: Source[ByteString, Any] =   ...

sttp
  .streamBody(source)
  .post(uri"...")
```

To receive the response body as a stream:

```scala
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp._

import akka.stream.scaladsl.Source
import akka.util.ByteString

implicit val sttpHandler = AkkaHttpSttpHandler()

val response: Future[Response[Source[ByteString, Any]]] = 
  sttp
    .post(uri"...")
    .response(asStream[Source[ByteString, Any]])
    .send()
```

### `AsyncHttpClientHandler`

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp" %% "async-http-client-handler-future" % "0.0.6"
// or
"com.softwaremill.sttp" %% "async-http-client-handler-scalaz" % "0.0.6"
// or
"com.softwaremill.sttp" %% "async-http-client-handler-monix" % "0.0.6"
// or
"com.softwaremill.sttp" %% "async-http-client-handler-cats" % "0.0.6"
```

This handler depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client).
A fully **asynchronous** handler, which uses [Netty](http://netty.io) behind the
scenes. 

The responses are wrapped depending on the dependency chosen in either a:

* standard Scala `Future`
* [Scalaz](https://github.com/scalaz/scalaz) `Task`. There's a transitive
dependency on `scalaz-concurrent`.
* [Monix](https://monix.io) `Task`. There's a transitive dependency on 
`monix-eval`.
* Any type implementing the [Cats Effect](https://github.com/typelevel/cats-effect) `Async` 
typeclass, such as `cats.effect.IO`. There's a transitive dependency on `cats-effect`.

Next you'll need to add an implicit value:

```scala
implicit val sttpHandler = FutureAsyncHttpClientHandler()

// or, if you're using the scalaz version:
implicit val sttpHandler = ScalazAsyncHttpClientHandler()

// or, if you're using the monix version:
implicit val sttpHandler = MonixAsyncHttpClientHandler()

// or, if you're using the cats effect version:
implicit val sttpHandler = CatsAsyncHttpClientHandler[cats.effect.IO]()

// or, if you'd like to use custom configuration:
implicit val sttpHandler = FutureAsyncHttpClientHandler.usingConfig(asyncHttpClientConfig)

// or, if you'd like to instantiate the AsyncHttpClient yourself:
implicit val sttpHandler = FutureAsyncHttpClientHandler.usingClient(asyncHttpClient)
```

#### Streaming using Monix

Currently, only the Monix handler supports streaming (as both Monix and Async 
Http Client support reactive streams `Publisher`s out of the box). The type of 
supported streams in this case is `Observable[ByteBuffer]`. That is, you can 
set such an observable as a request body:

```scala
import com.softwaremill.sttp._

import java.nio.ByteBuffer
import monix.reactive.Observable

val obs: Observable[ByteBuffer] =  ...

sttp
  .streamBody(obs)
  .post(uri"...")
```

And receive responses as an observable stream:

```scala
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.monix._

import java.nio.ByteBuffer
import monix.eval.Task
import monix.reactive.Observable

implicit val sttpHandler = MonixAsyncHttpClientHandler()

val response: Task[Response[Observable[ByteBuffer]]] = 
  sttp
    .post(uri"...")
    .response(asStream[Observable[ByteBuffer]])
    .send()
```

### `OkHttpClientHandler`

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp" %% "okhttp-client-handler" % "0.0.6"
```

This handler depends on [OkHttp](http://square.github.io/okhttp/), and offers 
both a **synchronous** (`OkHttpSyncClientHandler`) and **asynchronous**
(`OkHttpFutureClientHandler`), `Future`-based handlers.

OkHttp fully supports HTTP/2.

### Custom backends, logging, metrics

It is also entirely possible to write your own backend (if so, please consider
contributing!) or wrapping an existing one. You can even write completely 
generic wrappers for any delegate backend, as the each backend comes equipped
with a monad for the response wrapper. 

This brings the possibility to `map` and `flatMap` over responses. That way you
could implement e.g. a logging or metric-capturing wrapper.

## JSON

JSON encoding of bodies and decoding of responses can be handled using 
[Circe](https://circe.github.io/circe/) by the `circe` module. To use
add the following dependency to your project:

```scala
"com.softwaremill.sttp" %% "circe" % "0.0.6"
```

This module adds a method to the request and a function that can be given to
a request to decode the response to a specific object.

```scala
import com.softwaremill.sttp._
import com.softwaremill.sttp.circe._

implicit val handler = HttpURLConnectionSttpHandler

// Assume that there is an implicit circe encoder in scope
// for the request Payload, and a decoder for the Response
val requestPayload: Payload = ???

val response: Either[io.circe.Error, Response] = 
  sttp
    .post(uri"...")
    .body(requestPayload)
    .response(asJson[Response])
    .send()
```


## Request type

All request descriptions have type `RequestT[U, T, S]` (T as in Template).
If this looks a bit complex, don't worry, what the three type parameters stand
for is the only thing you'll hopefully have to remember when using the API!

Going one-by-one:

* `U[_]` specifies if the request method and URL are specified. Using the API,
this can be either `type Empty[X] = None`, meaning that the request has neither
a method nor an URI. Or, it can be `type Id[X] = X` (type-level identity),
meaning that the request has both a method and an URI specified. Only requests
with a specified URI & method can be sent.
* `T` specifies the type to which the response will be read. By default, this
is `String`. But it can also be e.g. `Array[Byte]` or `Unit`, if the response
should be ignored. Response body handling can be changed by calling the 
`.response` method. With backends which support streaming, this can also be
a supported stream type.
* `S` specifies the stream type that this request uses. Most of the time this
will be `Nothing`, meaning that this request does not send a streaming body
or receive a streaming response. So most of the times you can just ignore
that parameter. But, if you are using a streaming backend and want to 
send/receive a stream, the `.streamBody` or `response(asStream[S])` will change
the type parameter. 

There are two type aliases for the request template that are used:

* `type Request[T, S] = RequestT[Id, T, S]`. A sendable request.
* `type PartialRequest[T, S] = RequestT[Empty, T, S]`

## Notes

* the encoding for `String`s defaults to `utf-8`.
* unless explicitly specified, the `Content-Type` defaults to:
  * `text/plain` for text
  * `application/x-www-form-urlencoded` for form data
  * `multipart/form-data` for multipart form data
  * `application/octet-stream` for everything else (binary)

## TODO

* multi-part uploads
* scalaz/fs2 streaming
* proxy support
* connection options, SSL
* *your API improvement idea here*

## Other Scala HTTP clients

* [scalaj](https://github.com/scalaj/scalaj-http)
* [akka-http client](http://doc.akka.io/docs/akka-http/current/scala/http/client-side/index.html)
* [dispatch](http://dispatch.databinder.net/Dispatch.html)
* [play ws](https://github.com/playframework/play-ws)
* [fs2-http](https://github.com/Spinoco/fs2-http)
* [http4s](http://http4s.org/v0.17/client/)
* [Gigahorse](http://eed3si9n.com/gigahorse/)
* [RösHTTP](https://github.com/hmil/RosHTTP)

## Contributing

Take a look at the [open issues](https://github.com/softwaremill/sttp/issues) 
and pick a task you'd like to work on!

## Credits

* [Tomasz Szymański](https://github.com/szimano)
* [Adam Warski](https://github.com/adamw)
* [Omar Alejandro Mainegra Sarduy](https://github.com/omainegra)
* [Bjørn Madsen](https://github.com/aeons)
* [Piotr Buda](https://github.com/pbuda)
