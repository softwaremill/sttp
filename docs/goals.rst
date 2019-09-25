Goals of the project
====================

* provide a simple, discoverable, no-surprises, reasonably type-safe API for making HTTP requests and reading responses
* separate definition of a request from request execution
* provide immutable, easily modifiable data structures for requests and  responses
* support multiple execution backends, both synchronous and asynchronous
* provide support for backend-specific request/response streaming
* minimum dependencies

See also the `introduction to sttp <https://softwaremill.com/introducing-sttp-the-scala-http-client>`_ and `sttp streaming & URI interpolators <https://softwaremill.com/sttp-streaming-uri-interpolator>`_ blogs.

Non-goals of the project
------------------------

* implement a full HTTP client. Instead, sttp client wraps existing HTTP clients, providing a consistent, programmer-friendly API. All network-related concerns such as sending the requests, connection pooling, receiving responses are delegated to the chosen backend
* provide ultimate flexibility in defining the request. While it's possible to define *most* valid HTTP requests, e.g. some of the less common body chunking approaches aren't available

How is sttp different from other libraries?
-------------------------------------------

* immutable request builder which doesn't impose any order in which request parameters need to be specified. Such an approach allows defining partial requests with common cookies/headers/options, which can later be specialized using a specific URI and HTTP method.
* support for multiple backends, both synchronous and asynchronous, with backend-specific streaming support
* URI interpolator with context-aware escaping, optional parameters support and parameter collections
