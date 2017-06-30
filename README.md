# sttp

The HTTP client for Scala that you always wanted
 
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

## Other Scala HTTP clients

* [scalaj](https://github.com/scalaj/scalaj-http)
* [akka-http client](http://doc.akka.io/docs/akka-http/current/scala/http/client-side/index.html)
* [dispatch](http://dispatch.databinder.net/Dispatch.html)
* [play ws](https://github.com/playframework/play-ws)
* [fs2-http](https://github.com/Spinoco/fs2-http)