package com.softwaremill.sttp.curl

import scala.scalanative.native._

private[sttp] trait CURL {}

@link("curl")
@extern
private[sttp] object CCurl {
  @name("curl_easy_init")
  def init: Ptr[CURL] = extern

  @name("curl_easy_setopt")
  def setopt(handle: Ptr[CURL], option: CInt, parameter: Any): CInt = extern

  @name("curl_easy_perform")
  def perform(easy_handle: Ptr[CURL]): CInt = extern

  @name("curl_easy_cleanup")
  def cleanup(handle: Ptr[CURL]): Unit = extern

  @name("curl_slist_append")
  def slistAppend(list: Ptr[CurlSlist], string: CString): Ptr[CurlSlist] = extern

  @name("curl_slist_free_all")
  def slistFree(list: Ptr[CurlSlist]): Unit = extern

  @name("curl_easy_getinfo")
  def getInfo(handle: Ptr[CURL], info: CInt, parameter: Any): CInt = extern

  @name("curl_easy_escape")
  def encode(handle: Ptr[CURL], string: CString, lenght: Int): CString = extern

  @name("curl_free")
  def free(string: CString): Unit = extern
}
