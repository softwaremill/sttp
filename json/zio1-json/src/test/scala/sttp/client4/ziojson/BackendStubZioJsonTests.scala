package sttp.client4.ziojson

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.model.Uri
import sttp.client4.basicRequest
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.json.ast.Json
import zio.Chunk

case class Person(name: String)

object Person {
  implicit val encoder: JsonEncoder[Person] = DeriveJsonEncoder.gen[Person]
  implicit val codec: JsonDecoder[Person] = DeriveJsonDecoder.gen[Person]
}

class BackendStubJson4sTests extends AnyFlatSpec with Matchers with ScalaFutures {

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(Uri("http://example.org")).response(asJson[Person]).send(backend)

    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  it should "serialize from Json.Obj using implicit zioJsonBodySerializer" in {
    val fields: Chunk[(String, Json)] = Chunk(("location", Json.Str("hometown")), ("bio", Json.Str("Scala programmer")))
    val jObject: Json.Obj = Json.Obj(fields)

    val backend = SyncBackendStub.whenAnyRequest.thenRespond(jObject)
    val r = basicRequest.get(Uri("http://example.org")).body(jObject).send(backend)

    r.is200 should be(true)
    r.body should be(jObject)
  }
}
