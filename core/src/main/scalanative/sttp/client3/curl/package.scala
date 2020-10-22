package sttp.client3

import scala.scalanative.unsafe.{CSize, CString, CStruct2, Ptr}

package object curl {
  type CurlSlist = CStruct2[CString, Ptr[_]]
  type CurlFetch = CStruct2[CString, CSize]
  val CurlZeroTerminated = -1L
}
