package com.softwaremill.sttp

import scala.scalanative.native.{CSize, CString, CStruct2, Ptr}

package object curl {
  type CurlSlist = CStruct2[CString, Ptr[_]]
  type CurlFetch = CStruct2[CString, CSize]
}
