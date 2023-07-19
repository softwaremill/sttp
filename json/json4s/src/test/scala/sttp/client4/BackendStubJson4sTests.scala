package sttp.client4

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import org.json4s.{native, DefaultFormats, JField, JObject}
import org.json4s.JsonAST.JString

case class Person(name: String)

class BackendStubJson4sTests extends AnyFlatSpec with Matchers with ScalaFutures {

  implicit val serialization = native.Serialization
  implicit val formats = DefaultFormats

  import json4s._

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(Uri("http://example.org")).response(asJson[Person]).send(backend)

    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  it should "serialize from JObject using implicit json4sBodySerializer" in {
    val jObject: JObject = JObject(JField("location", JString("hometown")), JField("bio", JString("Scala programmer")))

    val backend = SyncBackendStub.whenAnyRequest.thenRespond(jObject)
    val r = basicRequest.get(Uri("http://example.org")).body(jObject).send(backend)

    r.is200 should be(true)
    r.body should be(jObject)
  }
}
