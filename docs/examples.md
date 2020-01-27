# Usage examples

All of the examples are available [in the sources](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client/examples) in runnable form.

## POST a form using the synchronous backend

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "2.0.0-RC6")
```

Example code:

```scala
import sttp.client._

val signup = Some("yes")

val request = basicRequest
  // send the body as form data (x-www-form-urlencoded)
  .body(Map("name" -> "John", "surname" -> "doe"))
  // use an optional parameter in the URI
  .post(uri"https://httpbin.org/post?signup=$signup")

implicit val backend = HttpURLConnectionBackend()
val response = request.send()

println(response.body)
println(response.headers)
```

## GET and parse JSON using the akka-http backend and json4s

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "akka-http-backend" % "2.0.0-RC6",
  "com.softwaremill.sttp.client" %% "json4s" % "2.0.0-RC6",
  "org.json4s" %% "json4s-native" % "3.6.0"
)
```

Example code:

```scala
import sttp.client._
import sttp.client.akkahttp._
import sttp.client.json4s._

import scala.concurrent.ExecutionContext.Implicits.global

case class HttpBinResponse(origin: String, headers: Map[String, String])

implicit val serialization = org.json4s.native.Serialization
val request = basicRequest
  .get(uri"https://httpbin.org/get")
  .response(asJson[HttpBinResponse])

implicit val backend = AkkaHttpBackend()
val response: Future[Response[Either[ResponseError[Exception], HttpBinResponse]]] =
  request.send()

for {
  r <- response
} {
  println(s"Got response code: ${r.code}")
  println(r.body)
  backend.close()
}                             
```

## POST and serialize JSON using the Monix async-http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "2.0.0-RC6",
  "com.softwaremill.sttp.client" %% "circe" % "2.0.0-RC6",
  "io.circe" %% "circe-generic" % "0.12.1"
)
```

Example code:

```scala
import sttp.client._
import sttp.client.circe._
import io.circe.generic.auto._

case class Info(x: Int, y: String)

val postTask = AsyncHttpClientMonixBackend().flatMap { implicit backend =>
  val r = basicRequest
    .body(Info(91, "abc"))
    .post(uri"https://httpbin.org/post")

  r.send().flatMap { response =>
    println(s"""Got ${response.code} response, body:\n${response.body}""")
    backend.close()
  }
}

import monix.execution.Scheduler.Implicits.global
postTask.runSyncUnsafe()
```

## Test an endpoint, which requires multiple query parameters

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "2.0.0-RC6")
```

Example code:

```scala
import sttp.client._
import sttp.client.testing._

implicit val backend = SttpBackendStub.synchronous
  .whenRequestMatches(_.uri.paramsMap.contains("filter"))
  .thenRespond("Filtered")
  .whenRequestMatches(_.uri.path.contains("secret"))
  .thenRespond("42")

val parameters1 = Map("filter" -> "name=mary", "sort" -> "asc")
println(
  basicRequest
    .get(uri"http://example.org?search=true&$parameters1")
    .send()
    .body)

val parameters2 = Map("sort" -> "desc")
println(
  basicRequest
    .get(uri"http://example.org/secret/read?$parameters2")
    .send()
    .body)
```