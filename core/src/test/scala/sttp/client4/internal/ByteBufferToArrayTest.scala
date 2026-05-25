package sttp.client4.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.ByteBuffer

class ByteBufferToArrayTest extends AnyFlatSpec with Matchers {

  it should "extract bytes from a direct buffer" in {
    val direct = ByteBuffer.allocateDirect(5)
    direct.put("ABCDE".getBytes)
    direct.flip()
    byteBufferToArray(direct) shouldBe "ABCDE".getBytes
  }

  it should "extract bytes from a read-only buffer" in {
    val readOnly = ByteBuffer.wrap("ABCDE".getBytes).asReadOnlyBuffer()
    byteBufferToArray(readOnly) shouldBe "ABCDE".getBytes
  }

  it should "return only the remaining slice when position > 0 and limit < capacity" in {
    val partial = ByteBuffer.wrap("ABCDE".getBytes)
    partial.position(2)
    partial.limit(4)
    byteBufferToArray(partial) shouldBe "CD".getBytes
  }

  it should "respect arrayOffset for sliced heap buffers" in {
    val original = ByteBuffer.wrap("ABCDE".getBytes)
    original.position(2)
    val sliced = original.slice()
    sliced.arrayOffset() should be > 0
    byteBufferToArray(sliced) shouldBe "CDE".getBytes
  }

  it should "return a fresh array that does not alias the source buffer's storage" in {
    val data = "ABCDE".getBytes
    val full = ByteBuffer.wrap(data)
    val out = byteBufferToArray(full)
    out(0) = 'Z'.toByte
    full.get(0) shouldBe 'A'.toByte
  }

  it should "not mutate the source buffer's position or limit" in {
    val partial = ByteBuffer.wrap("ABCDE".getBytes)
    partial.position(1)
    partial.limit(4)
    val _ = byteBufferToArray(partial)
    partial.position() shouldBe 1
    partial.limit() shouldBe 4
  }
}
