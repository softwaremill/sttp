package sttp.client4.curl

import scala.scalanative.unsafe.Ptr

class CurlSpaces(val bodyResp: Ptr[CurlFetch], val headersResp: Ptr[CurlFetch], val httpCode: Ptr[Long])
