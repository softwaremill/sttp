package sttp.client3.upicklejson

import upickle.default._
import org.scalatest.concurrent.ScalaFutures
import sttp.client3.basicRequest
import sttp.client3.testing.SttpBackendStub
import sttp.model.Uri
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class Person(name: String)
object Person {
  implicit val personRW: ReadWriter[Person] = macroRW[Person]
}

class SttpBackendStubUpickleTests extends AnyFlatSpec with Matchers with ScalaFutures {

  it should "deserialize to json using a string stub" in {
    val backend = SttpBackendStub.synchronous.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(Uri("http://example.org")).response(asJson[Person]).send(backend)
    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

}
