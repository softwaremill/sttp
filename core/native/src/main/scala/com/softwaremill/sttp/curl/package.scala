package com.softwaremill.sttp

import scala.scalanative.native.{CSize, CString, CStruct2, Ptr}

package object curl {
  type CurlSlist = CStruct2[CString, Ptr[_]]
  type CurlFetch = CStruct2[CString, CSize]

  class CurlList(val ptr: Ptr[CurlSlist]) extends AnyVal {}

  class CurlSpaces(val bodyResp: Ptr[CurlFetch], val headersResp: Ptr[CurlFetch], val httpCode: Ptr[Long])
      extends AnyRef {}
}
