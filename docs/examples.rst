.. _usage_examples:

Usage examples
==============

POST a form using the synchronous backend
-----------------------------------------

Required dependencies::

  libraryDependencies ++= List("com.softwaremill.sttp" %% "core" % "1.1.3")

Example code::

  import com.softwaremill.sttp._

  val signup = Some("yes")

  val request = sttp
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
    "com.softwaremill.sttp" %% "akka-http-backend" % "1.1.3",
    "com.softwaremill.sttp" %% "json4s" % "1.1.3"
  )

Example code::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.akkahttp._
  import com.softwaremill.sttp.json4s._

  import scala.concurrent.ExecutionContext.Implicits.global

  case class HttpBinResponse(origin: String, headers: Map[String, String])

  val request = sttp
    .get(uri"https://httpbin.org/get")
    .response(asJson[HttpBinResponse])

  implicit val backend = AkkaHttpBackend()
  val response: Future[Response[HttpBinResponse]] = request.send()

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

  libraryDependencies ++= List("com.softwaremill.sttp" %% "core" % "1.1.3")

Example code::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.testing._

  implicit val backend = SttpBackendStub.synchronous
    .whenRequestMatches(_.uri.paramsMap.contains("filter"))
    .thenRespond("Filtered")
    .whenRequestMatches(_.uri.path.contains("secret"))
    .thenRespond("42")

  val parameters1 = Map("filter" -> "name=mary", "sort" -> "asc")
  println(
    sttp
      .get(uri"http://example.org?search=true&$parameters1")
      .send()
      .unsafeBody)

  val parameters2 = Map("sort" -> "desc")
  println(
    sttp
      .get(uri"http://example.org/secret/read?$parameters2")
      .send()
      .unsafeBody)