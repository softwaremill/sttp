package sttp.client4.circe

import org.scalatest.concurrent.ScalaFutures
import sttp.client4._
import sttp.client4.testing.SyncBackendStub
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.EncoderOps
import io.circe.JsonObject
import sttp.model.Uri

class BackendStubCirceTests extends AnyFlatSpec with Matchers with ScalaFutures {

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(uri"http://example.org").response(asJson[Person]).send(backend)
    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  it should "serialize from JsonObject using implicit circeBodySerializer" in {
    val jObject: JsonObject = JsonObject(("location", "hometown".asJson), ("bio", "Scala programmer".asJson))
    val result = basicRequest.get(Uri("http://example.org")).body(jObject).body.show

    val expectedResult = "string: {\"location\":\"hometown\",\"bio\":\"Scala programmer\"}"

    result should be(expectedResult)
  }

  case class Person(name: String)
}
