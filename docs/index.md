# sttp: the Scala HTTP client you always wanted!

Welcome!

[sttp client](https://github.com/softwaremill/sttp) is an open-source library which provides a clean, programmer-friendly API to describe HTTP
requests and execute them using one of the wrapped backends, such as [akka-http](https://doc.akka.io/docs/akka-http/current/scala/http/), [async-http-client](https://github.com/AsyncHttpClient/async-http-client), [http4s](https://http4s.org) or [OkHttp](http://square.github.io/okhttp/).

Here's a very quick example of sttp client in action:

```scala
import sttp.client._

val sort: Option[String] = None
val query = "http language:scala"

// the `query` parameter is automatically url-encoded
// `sort` is removed, as the value is not defined
val request = basicRequest.get(
  uri"https://api.github.com/search/repositories?q=$query&sort=$sort")

implicit val backend = HttpURLConnectionBackend()
val response = request.send()

// response.header(...): Option[String]
println(response.header("Content-Length"))

// response.body: by default read into an Either[String, String] to indicate failure or success 
println(response.body)            
```

For more examples, see the [usage examples](examples.md) section.

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* sttp client: this project
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)

# Table of contents

```eval_rst
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart
   goals
   community
   examples

.. toctree::
   :maxdepth: 2
   :caption: HTTP model

   model/model
   model/uri

.. toctree::
   :maxdepth: 2
   :caption: Request definition

   requests/basics
   requests/headers
   requests/cookies
   requests/authentication
   requests/body
   requests/multipart
   requests/streaming
   requests/websockets
   requests/type

.. toctree::
   :maxdepth: 2
   :caption: Responses

   responses/basics
   responses/body

.. toctree::
   :maxdepth: 2
   :caption: Backends

   backends/summary
   backends/start_stop
   backends/httpurlconnection
   backends/akkahttp
   backends/asynchttpclient
   backends/okhttp
   backends/http4s
   backends/finagle
   backends/httpclient
   backends/opentracing
   backends/brave
   backends/prometheus
   backends/javascript/fetch
   backends/custom

.. toctree::
   :maxdepth: 2
   :caption: Testing

   testing

.. toctree::
   :maxdepth: 2
   :caption: Configuration

   conf/timeouts
   conf/ssl
   conf/proxy
   conf/redirects

.. toctree::
   :maxdepth: 2
   :caption: More information

   json
   other
```
