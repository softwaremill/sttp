package sttp.client3.internal

import scala.scalanative.unsafe._
import scala.scalanative.libc.string.strlen
import scala.scalanative.runtime.ByteArray
import scala.scalanative.unsigned._

object CryptoMd5 {
  @link("crypto")
  @extern
  private object C {
    def MD5(string: CString, size: CSize, result: CString): CString = extern
  }

  def digest(input: Array[Byte]): Array[Byte] = {
    val result = ByteArray.alloc(16)
    C.MD5(input.asInstanceOf[ByteArray].at(0), input.length.toUInt, result.at(0))
    result.asInstanceOf[Array[Byte]]
  }
}
