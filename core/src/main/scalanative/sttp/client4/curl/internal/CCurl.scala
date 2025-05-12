package sttp.client4.curl.internal

import sttp.client4.curl.internal.CurlCode.CurlCode

import scala.scalanative.unsafe._
import scala.scalanative.meta.LinktimeInfo.isWindows

private[curl] trait Curl {}

private[curl] trait Mime {}

private[curl] trait MimePart {}

private[curl] object libcurlPlatformCompat {
  @extern @link("libcurl") @link("crypt32") @define("STTP_CURL_FFI")
  private object libcurlWin64 extends CCurl

  @extern @link("curl") @define("STTP_CURL_FFI")
  private object libcurlDefault extends CCurl

  val instance: CCurl =
    if (isWindows) libcurlWin64
    else libcurlDefault
}

@extern
private[curl] trait CCurl {
  @name("sttp_curl_setopt_int")
  def setoptInt(handle: Ptr[Curl], option: CInt, parameter: Int): CInt = extern

  @name("sttp_curl_setopt_long")
  def setoptLong(handle: Ptr[Curl], option: CInt, parameter: Long): CInt = extern

  @name("sttp_curl_setopt_pointer")
  def setoptPtr(handle: Ptr[Curl], option: CInt, parameter: Ptr[_]): CInt = extern

  @name("sttp_curl_getinfo_pointer")
  def getInfo(handle: Ptr[Curl], info: CInt, parameter: Ptr[_]): CInt = extern

  @name("sttp_curl_get_version")
  def getVersion(): CString = extern

  @name("curl_easy_init")
  def init: Ptr[Curl] = extern

  @name("curl_easy_cleanup")
  def cleanup(handle: Ptr[Curl]): Unit = extern

  @name("curl_easy_perform")
  def perform(easy_handle: Ptr[Curl]): CInt = extern

  @name("curl_mime_init")
  def mimeInit(easy: Ptr[Curl]): Ptr[Mime] = extern

  @name("curl_mime_free")
  def mimeFree(mime: Ptr[Mime]): Unit = extern

  @name("curl_mime_addpart")
  def mimeAddPart(mime: Ptr[Mime]): Ptr[MimePart] = extern

  @name("curl_mime_name")
  def mimeName(part: Ptr[MimePart], name: CString): CurlCode = extern

  @name("curl_mime_filename")
  def mimeFilename(part: Ptr[MimePart], filename: CString): CurlCode = extern

  @name("curl_mime_type")
  def mimeType(part: Ptr[MimePart], mimetype: CString): CurlCode = extern

  @name("curl_mime_encoder")
  def mimeEncoder(part: Ptr[MimePart], encoding: CString): CurlCode = extern

  @name("curl_mime_data")
  def mimeData(part: Ptr[MimePart], data: CString, datasize: CSize): CurlCode = extern

  @name("curl_mime_filedata")
  def mimeFiledata(part: Ptr[MimePart], filename: CString): CurlCode = extern

  @name("curl_mime_subparts")
  def mimeSubParts(part: Ptr[MimePart], subparts: Ptr[MimePart]): CurlCode = extern

  @name("curl_mime_headers")
  def mimeHeaders(part: Ptr[MimePart], headers: Ptr[CurlSlist], take_ownership: CInt): CurlCode = extern

  @name("curl_slist_append")
  def slistAppend(list: Ptr[CurlSlist], string: CString): Ptr[CurlSlist] = extern

  @name("curl_slist_free_all")
  def slistFree(list: Ptr[CurlSlist]): Unit = extern
}
