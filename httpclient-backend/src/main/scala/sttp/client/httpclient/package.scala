package sttp.client

import java.nio.ByteBuffer

package object httpclient {
  implicit class RichByteBuffer(byteBuffer: ByteBuffer) {
    def safeRead(): Array[Byte] = {
      if (byteBuffer.hasArray) {
        byteBuffer.array()
      } else {
        val array = new Array[Byte](byteBuffer.remaining())
        byteBuffer.get(array)
        array
      }
    }
  }
}
