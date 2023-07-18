package sttp.client4

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import spray.json.{enrichAny, JsObject}
import spray.json.DefaultJsonProtocol.{jsonFormat1, RootJsObjectFormat, StringJsonFormat}
import spray.json.RootJsonFormat
import sprayJson._

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

  it should "serialize from JsObject using implicit upickleBodySerializer" in {
    val json: JsObject = JsObject(
      "location" -> "hometown".toJson,
      "bio" -> "Scala programmer".toJson
    )

    val backend = SyncBackendStub.whenAnyRequest.thenRespond(json)
    val r = basicRequest.get(Uri("http://example.org")).body(json).send(backend)

    r.is200 should be(true)
    r.body should be(json)
  }
}
