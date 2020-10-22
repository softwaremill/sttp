# Goals of the project

* provide a simple, discoverable, no-surprises, reasonably type-safe API for making HTTP requests and reading responses
* separate definition of a request from request execution
* provide immutable, easily modifiable data structures for requests and responses
* support multiple execution backends, both synchronous and asynchronous
* provide support for backend-specific request/response streaming
* minimum dependencies

See also the blog posts:

* [Introduction to sttp](https://softwaremill.com/introducing-sttp-the-scala-http-client)
* [sttp streaming & URI interpolators](https://softwaremill.com/sttp-streaming-uri-interpolator)
* [sttp2: an overview of proposed changes](https://blog.softwaremill.com/sttp2-an-overview-of-proposed-changes-8de23c94684f)
* [Migrating to sttp client 2.x and tapir 0.12.x](https://blog.softwaremill.com/migrating-to-sttp-client-2-x-and-tapir-0-12-x-7956e6c79c52)
* [What’s coming up in sttp client 3?](https://blog.softwaremill.com/whats-coming-up-in-sttp-client-3-30d01ab42d1b)

## Non-goals of the project

* implement a full HTTP client. Instead, sttp client wraps existing HTTP clients, providing a consistent, programmer-friendly API. All network-related concerns such as sending the requests, connection pooling, receiving responses are delegated to the chosen backend
* provide ultimate flexibility in defining the request. While it's possible to define *most* valid HTTP requests, e.g. some of the less common body chunking approaches aren't available

## How is sttp different from other libraries?

* immutable request builder which doesn't impose any order in which request parameters need to be specified. Such an approach allows defining partial requests with common cookies/headers/options, which can later be specialized using a specific URI and HTTP method.
* support for multiple backends, both synchronous and asynchronous, with backend-specific streaming support
* URI interpolator with context-aware escaping, optional parameters support and parameter collections
* description of how to handle the response is combined with the description of the request to send
