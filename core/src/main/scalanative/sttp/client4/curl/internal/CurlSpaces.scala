package sttp.client4.curl.internal

import scala.scalanative.unsafe.{CChar, Ptr}

class CurlSpaces(
    val bodyResp: Ptr[CurlFetch],
    val headersResp: Ptr[CurlFetch],
    val httpCode: Ptr[Long],
    val errorBuffer: Ptr[CChar]
)
