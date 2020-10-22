package sttp.client3.circe

import org.scalatest.concurrent.ScalaFutures
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SttpBackendStubCirceTests extends AnyFlatSpec with Matchers with ScalaFutures {
  it should "deserialize to json using a string stub" in {
    val backend = SttpBackendStub.synchronous.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(uri"http://example.org").response(asJson[Person]).send(backend)
    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  case class Person(name: String)
}
