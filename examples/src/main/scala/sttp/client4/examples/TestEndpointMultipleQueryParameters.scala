package sttp.client4.examples

import sttp.client4.*
import sttp.client4.testing.*

@main def testEndpointMultipleQueryParameters(): Unit =
  val backend = SyncBackendStub
    .whenRequestMatches(_.uri.paramsMap.contains("filter"))
    .thenRespond("Filtered")
    .whenRequestMatches(_.uri.path.contains("secret"))
    .thenRespond("42")

  val parameters1 = Map("filter" -> "name=mary", "sort" -> "asc")
  println(
    basicRequest
      .get(uri"http://example.org?search=true&$parameters1")
      .send(backend)
      .body
  )

  val parameters2 = Map("sort" -> "desc")
  println(
    basicRequest
      .get(uri"http://example.org/secret/read?$parameters2")
      .send(backend)
      .body
  )
