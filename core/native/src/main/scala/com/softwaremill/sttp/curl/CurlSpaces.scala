package com.softwaremill.sttp.curl

import scala.scalanative.native.Ptr

class CurlSpaces(val bodyResp: Ptr[CurlFetch], val headersResp: Ptr[CurlFetch], val httpCode: Ptr[Long])
