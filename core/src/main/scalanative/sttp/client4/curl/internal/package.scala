package sttp.client4.curl

import scala.scalanative.unsafe.{CSize, CString, CStruct2, Ptr}

package object internal {
  type CurlSlist = CStruct2[CString, Ptr[_]]
  type CurlFetch = CStruct2[CString, CSize]
  val CurlZeroTerminated = -1L
}
