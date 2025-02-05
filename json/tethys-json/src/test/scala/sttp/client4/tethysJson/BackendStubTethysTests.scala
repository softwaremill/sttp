package sttp.client4.tethysJson

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.testing.SyncBackendStub
import tethys.derivation.semiauto.{jsonReader, jsonWriter}
import tethys.jackson.jacksonTokenIteratorProducer
import tethys.{JsonReader, JsonWriter}

class BackendStubTethysTests extends AnyFlatSpec with Matchers with ScalaFutures {

  it should "deserialize to json using a string stub" in {
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust("""{"name": "John"}""")
    val r = basicRequest.get(uri"http://example.org").response(asJson[Person]).send(backend)
    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  case class Person(name: String)

  object Person {
    implicit val encoder: JsonWriter[Person] = jsonWriter
    implicit val decoder: JsonReader[Person] = jsonReader
  }
}
