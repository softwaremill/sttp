package sttp.client4.upicklejson

import upickle.default._
import org.scalatest.concurrent.ScalaFutures
import sttp.client4.basicRequest
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ujson.Obj

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

  it should "serialize ujson.Obj using implicit upickleBodySerializer" in {
    val json: Obj = ujson.Obj(
      "location" -> "hometown",
      "bio" -> "Scala programmer"
    )

    val backend = SyncBackendStub.whenAnyRequest.thenRespond(json)
    val r = basicRequest.get(Uri("http://example.org")).body(json).response(asJson[Person]).send(backend)

    r.is200 should be(true)
    r.body should be(json)
  }
}
