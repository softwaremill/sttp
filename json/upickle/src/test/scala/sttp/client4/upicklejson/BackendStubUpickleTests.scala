package sttp.client4.upicklejson

import upickle.default._
import org.scalatest.concurrent.ScalaFutures
import sttp.client4.basicRequest
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class Person(name: String)
object Person {
  implicit val personRW: ReadWriter[Person] = macroRW[Person]
}

class BackendStubUpickleTests extends AnyFlatSpec with Matchers with ScalaFutures {

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(Uri("http://example.org")).response(asJson[Person]).send(backend)
    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }
}
