.. _usage_examples:

Usage examples
==============

POST a form using the synchronous backend
-----------------------------------------

Required dependencies::

  libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "2.0.0-M7")

Example code::

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

GET and parse JSON using the akka-http backend and json4s
---------------------------------------------------------

Required dependencies::

  libraryDependencies ++= List(
    "com.softwaremill.sttp.client" %% "akka-http-backend" % "2.0.0-M7",
    "com.softwaremill.sttp.client" %% "json4s" % "2.0.0-M7",
    "org.json4s" %% "json4s-native" % "3.6.0"
  )

Example code::

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

Test an endpoint requiring multiple parameters
----------------------------------------------

Required dependencies::

  libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "2.0.0-M7")

Example code::

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
      .unsafeBody)

  val parameters2 = Map("sort" -> "desc")
  println(
    basicRequest
      .get(uri"http://example.org/secret/read?$parameters2")
      .send()
      .unsafeBody)
