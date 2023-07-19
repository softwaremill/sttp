package sttp.client4

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.json.{JsString, OFormat}
import playJson._

case class Person(name: String)

object Person {
  implicit val personFormat: OFormat[Person] = Json.format[Person]
}

class BackendStubPlayJsonTests extends AnyFlatSpec with Matchers with ScalaFutures {

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(Uri("http://example.org")).response(asJson[Person]).send(backend)

    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  it should "serialize from JsObject using implicit playJsonBodySerializer" in {
    val fields: Seq[(String, JsValue)] =
      Seq[(String, JsValue)](("location", JsString("hometown")), ("bio", JsString("Scala programmer")))
    val json: JsObject = JsObject(fields)

    val backend = SyncBackendStub.whenAnyRequest.thenRespond(json)
    val r = basicRequest.get(Uri("http://example.org")).body(json).send(backend)

    r.is200 should be(true)
    r.body should be(json)
  }
}
