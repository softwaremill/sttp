# sttp: the Scala HTTP client you always wanted!

Welcome!

[sttp client](https://github.com/softwaremill/sttp) is an open-source library which provides a clean, programmer-friendly API to describe HTTP
requests and how to handle responses. Requests are sent using one of the backends, which wrap other Scala or Java HTTP client implementations. The backends can integrate with a variety of Scala stacks, providing both synchronous and asynchronous, procedural and functional interfaces.
 
Backend implementations include ones based on [akka-http](https://doc.akka.io/docs/akka-http/current/scala/http/), [async-http-client](https://github.com/AsyncHttpClient/async-http-client), [http4s](https://http4s.org), [OkHttp](http://square.github.io/okhttp/), and HTTP clients which ship with Java. They integrate with [Akka](https://akka.io), [Monix](https://monix.io), [fs2](https://github.com/functional-streams-for-scala/fs2), [cats-effect](https://github.com/typelevel/cats-effect), [scalaz](https://github.com/scalaz/scalaz) and [ZIO](https://github.com/zio/zio). 

Here's a very quick example of sttp client in action:

```scala
import sttp.client._

val query = "http language:scala"
val sort: Option[String] = None

// the `query` parameter is automatically url-encoded
// `sort` is removed, as the value is not defined
val request = basicRequest.get(
  uri"https://api.github.com/search/repositories?q=$query&sort=$sort")

implicit val backend = HttpURLConnectionBackend()
val response = request.send()

// response.header(...): Option[String]
println(response.header("Content-Length"))

// response.body: by default read into an Either[String, String] 
// to indicate failure or success 
println(response.body)            
```

For more examples, see the [usage examples](examples.md) section. To start using sttp client in your project, see the [quickstart](quickstart.md). Or, browse the documentation to find the topics that interest you the most!

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
   how
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
   backends/javascript/fetch

.. toctree::
   :maxdepth: 2
   :caption: Backend wrappers

   backends/wrappers/opentracing
   backends/wrappers/brave
   backends/wrappers/prometheus
   backends/wrappers/slf4j
   backends/wrappers/custom

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
