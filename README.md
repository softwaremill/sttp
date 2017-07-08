# sttp

The HTTP client for Scala that you always wanted
 
```scala
val user = "adamw"
val state = "closed"
val sort: Option[String] = None
val request = sttp.get(uri"https://api.github.com/repos/$user/elasticmq/issues?state=$state&sort=$sort")
  
val response = request.send(responseAsString("utf-8"))

println(response.header("Content-Length")) // has type Option[String]
println(response.body)                     // has type String as specified when sending the request
```
 
## Goals of the project

* provide a simple, discoverable, no-surprises, reasonably type-safe API for making HTTP requests
* separate definition of a request from request execution
* provide immutable, easily modifiable data structures for requests and responses
* support both synchronous and asynchronous execution backends
* provide support for backend-specific request/response streaming

## How is sttp different from other libraries?

* immutable request builder which doesn't require the URI to be specified upfront. Allows defining partial requests
which contain common cookies/headers/options, which can later be specialized using a specific URI and HTTP method.
* support for multiple backends, both synchronous and asynchronous, with backend-specific streaming support
* TODO URI interpolator with optional parameters support

## Usage 

First, make sure to import:

```scala
import com.softwaremill.sttp._
```

To send requests, you will also need a backend. A default, synchronous backend based on Java's `HttpURLConnection`
is provided out-of-the box. An implicit value needs to be in scope to invoke `send()` (however it's possible to 
create request descriptions without any implicit backend in scope): 

```scala
implicit val handler = HttpConnectionSttpHandler
```

Any request definition starts from `sttp`: the empty request. This can be further customised, each time yielding a new,
immutable request description (unless a mutable body is set on the request, such as a byte array).

## Request types

All requests have type `RequestTemplate[U]`, where `U[_]` specifies if the request method and URL are specified. There
are two type aliases for the request template that are used:

* `type Request = RequestTemplate[Id]`, where `type Id[X] = X` is the identity, meaning that the request has both a 
method and URI. 
* `type PartialRequest = RequestTemplate[Empty]`, where `type Empty[X] = None`, meaning that the request has neither
a method nor an URI. Both of these fields will be set to `None` (the `Option` subtype).

## Notes

* the encoding for `String`s defaults to `utf-8`.
* unless explicitly specified, the `Content-Type` defaults to:
  * `text/plain` for text
  * `application/x-www-form-urlencoded` for form data
  * `multipart/form-data` for multipart form data
  * `application/octet-stream` for everything else (binary)

## Other Scala HTTP clients

* [scalaj](https://github.com/scalaj/scalaj-http)
* [akka-http client](http://doc.akka.io/docs/akka-http/current/scala/http/client-side/index.html)
* [dispatch](http://dispatch.databinder.net/Dispatch.html)
* [play ws](https://github.com/playframework/play-ws)
* [fs2-http](https://github.com/Spinoco/fs2-http)
* [http4s](http://http4s.org/v0.17/client/)