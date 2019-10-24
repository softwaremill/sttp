package sttp.model.internal.idn

import scala.scalanative.native
import scala.scalanative.native.stdlib.{free, malloc}
import scala.scalanative.native.{CString, Ptr, fromCString, sizeof, toCString}

private[model] object IdnApi {
  def toAscii(input: String): String = native.Zone { implicit z =>
    val output: Ptr[CString] = malloc(sizeof[CString]).cast[Ptr[CString]]
    val rc = CIdn.toAscii(toCString(input), output, 0)
    if (rc != 0) {
      val errMsg = CIdn.errorMsg(rc)
      throw new RuntimeException(fromCString(errMsg))
    } else {
      val out = fromCString(!output)
      CIdn.free(!output)
      free(output.cast[Ptr[Byte]])
      out
    }
  }
}
