package sttp.client.circe

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import sttp.client._
import sttp.client.testing.SttpBackendStub
import io.circe.generic.auto._

class SttpBackendStubCirceTests extends FlatSpec with Matchers with ScalaFutures {
  it should "deserialize to json using a string stub" in {
    implicit val b = SttpBackendStub.synchronous.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(uri"http://example.org").response(asJson[Person]).send()
    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  case class Person(name: String)
}
