package com.softwaremill.sttp.curl

import scala.scalanative.native.Ptr

class CurlList(val ptr: Ptr[CurlSlist]) extends AnyVal {}
