package sttp.client4.examples

object TestEndpointMultipleQueryParameters extends App {
  import sttp.client4._
  import sttp.client4.testing._

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
}
