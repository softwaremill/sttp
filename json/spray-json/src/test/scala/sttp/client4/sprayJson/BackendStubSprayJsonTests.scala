package sttp.client4.sprayJson

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import spray.json.DefaultJsonProtocol.{jsonFormat1, StringJsonFormat}
import spray.json.RootJsonFormat
import sttp.client4.basicRequest

case class Person(name: String)

object Person {
  implicit val personRootJsonFormat: RootJsonFormat[Person] = jsonFormat1(Person.apply)
}

class BackendStubSprayJsonTests extends AnyFlatSpec with Matchers with ScalaFutures {

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(Uri("http://example.org")).response(asJson[Person]).send(backend)

    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }
}
