package sttp.client4

import org.json4s.native.Serialization
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import org.json4s.{DefaultFormats, native}

case class Person(name: String)

class BackendStubJson4sTests extends AnyFlatSpec with Matchers with ScalaFutures {

  implicit val serialization: Serialization.type = native.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  import json4s._

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(Uri("http://example.org")).response(asJson[Person]).send(backend)

    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }
}
