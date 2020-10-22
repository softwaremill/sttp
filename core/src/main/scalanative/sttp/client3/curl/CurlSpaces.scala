package sttp.client3.curl

import scala.scalanative.unsafe.Ptr

class CurlSpaces(val bodyResp: Ptr[CurlFetch], val headersResp: Ptr[CurlFetch], val httpCode: Ptr[Long])
