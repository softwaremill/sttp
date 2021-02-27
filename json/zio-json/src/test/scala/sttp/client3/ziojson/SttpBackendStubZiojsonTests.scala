package sttp.client3.ziojson

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.{UriContext, basicRequest}
import sttp.client3.testing.SttpBackendStub
import zio.json.{DeriveJsonDecoder, JsonDecoder}

class SttpBackendStubZiojsonTests extends AnyFlatSpec with Matchers with ScalaFutures {
  it should "deserialize to json using a string stub" in {
    val backend = SttpBackendStub.synchronous.whenAnyRequest.thenRespond("""{"name": "John"}""")
    val r = basicRequest.get(uri"http://example.org").response(asJson[Person]).send(backend)
    r.is200 should be(true)
    r.body should be(Right(Person("John")))
  }

  case class Person(name: String)

  implicit val personDecoder: JsonDecoder[Person] = DeriveJsonDecoder.gen

}
