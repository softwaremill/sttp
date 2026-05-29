package sttp.client4.testing

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

import java.nio.ByteBuffer

class ForceBodyAsTest extends AnyFlatSpec with Matchers {

  private val payload = "hello".getBytes("UTF-8")

  it should "forceBodyAsString: extract a direct ByteBuffer body" in {
    val direct = ByteBuffer.allocateDirect(payload.length)
    direct.put(payload)
    direct.flip()
    val request = basicRequest.post(uri"http://example.com/").body(direct)
    request.forceBodyAsString shouldBe "hello"
  }

  it should "forceBodyAsByteArray: extract a direct ByteBuffer body" in {
    val direct = ByteBuffer.allocateDirect(payload.length)
    direct.put(payload)
    direct.flip()
    val request = basicRequest.post(uri"http://example.com/").body(direct)
    request.forceBodyAsByteArray shouldBe payload
  }

  it should "forceBodyAsString: return only the remaining slice of a partial heap buffer" in {
    val backing = "XX".getBytes ++ payload ++ "YY".getBytes
    val partial = ByteBuffer.wrap(backing)
    partial.position(2)
    partial.limit(2 + payload.length)
    val request = basicRequest.post(uri"http://example.com/").body(partial)
    request.forceBodyAsString shouldBe "hello"
  }

  it should "forceBodyAsByteArray: return only the remaining slice of a partial heap buffer" in {
    val backing = "XX".getBytes ++ payload ++ "YY".getBytes
    val partial = ByteBuffer.wrap(backing)
    partial.position(2)
    partial.limit(2 + payload.length)
    val request = basicRequest.post(uri"http://example.com/").body(partial)
    request.forceBodyAsByteArray shouldBe payload
  }
}
