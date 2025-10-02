// {cat=JSON; effects=Direct; backend=HttpClient}: Receive & parse JSON using jsoniter

//> using dep com.softwaremill.sttp.client4::jsoniter:4.0.12
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.33.3

package sttp.client4.examples.json

import sttp.client4.*
import sttp.client4.jsoniter.*
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.JsonKeyCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader

@main def getAndParseJsonSynchronousJsoniter(): Unit =
  // data structures
  case class Address(street: String, city: String)
  enum AddressType:
    case Home, Work
  case class PersonalData(name: String, age: Int, addresses: Map[AddressType, Address])
  case class HttpBinResponse(origin: String, headers: Map[String, String], data: String)

  // jsoniter codecs
  given JsonKeyCodec[AddressType] with
    def decodeKey(in: JsonReader): AddressType = AddressType.valueOf(in.readKeyAsString())
    def encodeKey(x: AddressType, out: JsonWriter): Unit = out.writeKey(x.toString())
  given JsonValueCodec[PersonalData] = JsonCodecMaker.make
  given JsonValueCodec[HttpBinResponse] = JsonCodecMaker.make

  // sending & receiving JSON
  val request = basicRequest
    .post(uri"https://httpbin.org/post")
    .body(
      asJson(
        PersonalData(
          "Alice",
          25,
          Map(
            AddressType.Home -> Address("Marszałkowska", "Warsaw"),
            AddressType.Work -> Address("Długa", "Gdańsk")
          )
        )
      )
    )
    .response(asJsonOrFail[HttpBinResponse])

  val backend: SyncBackend = DefaultSyncBackend()
  val response: Response[HttpBinResponse] = request.send(backend)

  println(s"Got response code: ${response.code}")
  println(response.body)
