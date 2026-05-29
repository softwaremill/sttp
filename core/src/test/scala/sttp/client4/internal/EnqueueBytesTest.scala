package sttp.client4.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.ByteBuffer
import scala.collection.immutable.Queue

class EnqueueBytesTest extends AnyFlatSpec with Matchers {

  it should "append the bytes of a direct buffer" in {
    val direct = ByteBuffer.allocateDirect(5)
    direct.put("hello".getBytes)
    direct.flip()
    val result = enqueueBytes(Queue.empty[Array[Byte]], direct)
    result.head shouldBe "hello".getBytes
  }

  it should "append only the remaining slice of a partial heap buffer" in {
    val partial = ByteBuffer.wrap("ABCDE".getBytes)
    partial.position(2)
    partial.limit(4)
    val result = enqueueBytes(Queue.empty[Array[Byte]], partial)
    result.head shouldBe "CD".getBytes
  }
}
