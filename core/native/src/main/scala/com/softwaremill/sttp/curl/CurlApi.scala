package com.softwaremill.sttp.curl

import com.softwaremill.sttp.curl.CurlCode.CurlCode
import com.softwaremill.sttp.curl.CurlInfo.CurlInfo
import com.softwaremill.sttp.curl.CurlOption.CurlOption

import scala.scalanative.native
import scala.scalanative.native.{Ptr, _}

private[sttp] object CurlApi {

  type CurlHandle = Ptr[Curl]

  type MimeHandle = Ptr[Mime]

  type MimePartHandle = Ptr[MimePart]

  type SlistHandle = Ptr[CurlSlist]

  def init: CurlHandle = CCurl.init

  implicit class CurlHandleOps(handle: CurlHandle) {
    def mime: MimeHandle = CCurl.mimeInit(handle)

    def perform: CurlCode = CurlCode(CCurl.perform(handle))

    def cleanup(): Unit = CCurl.cleanup(handle)

    def option(option: CurlOption, parameter: String)(implicit z: Zone): CurlCode = {
      setopt(handle, option, toCString(parameter)(z))
    }

    def option(option: CurlOption, parameter: Long): CurlCode = {
      setopt(handle, option, parameter)
    }

    def option(option: CurlOption, parameter: Int): CurlCode = {
      setopt(handle, option, parameter)
    }

    def option(option: CurlOption, parameter: Boolean): CurlCode = {
      setopt(handle, option, if (parameter) 1L else 0L)
    }

    def option(option: CurlOption, parameter: Ptr[_]): CurlCode = {
      setopt(handle, option, parameter)
    }

    def option[FuncPtr >: CFunctionPtr](option: CurlOption, parameter: FuncPtr): CurlCode = {
      setopt(handle, option, parameter)
    }

    def info(curlInfo: CurlInfo, parameter: Long): CurlCode = {
      getInfo(handle, curlInfo, parameter)
    }

    def info(curlInfo: CurlInfo, parameter: String): CurlCode = {
      getInfo(handle, curlInfo, parameter)
    }

    def info(curlInfo: CurlInfo, parameter: Ptr[_]): CurlCode = {
      getInfo(handle, curlInfo, parameter)
    }

    def encode(string: String): String = native.Zone { implicit z =>
      val e = enc(handle, toCString(string), string.length)
      val s = fromCString(e)
      CCurl.free(e)
      s
    }
  }

  private def setopt(handle: CurlHandle, option: CurlOption, parameter: Any): CurlCode = {
    CurlCode(CCurl.setopt(handle, option.id, parameter))
  }

  private def getInfo(handle: CurlHandle, curlInfo: CurlInfo, parameter: Any): CurlCode = {
    CurlCode(CCurl.getInfo(handle, curlInfo.id, parameter))
  }

  private def enc(handle: CurlHandle, string: CString, length: Int): CString = {
    CCurl.encode(handle, string, length)
  }

  implicit class MimeHandleOps(handle: MimeHandle) {
    def free(): Unit = CCurl.mimeFree(handle)

    def addPart(): MimePartHandle = CCurl.mimeAddPart(handle)
  }

  implicit class MimePartHandleOps(handle: MimePartHandle) {
    def withName(name: String)(implicit zone: Zone): CurlCode = CCurl.mimeName(handle, toCString(name))

    def withFileName(filename: String)(implicit zone: Zone): CurlCode = CCurl.mimeFilename(handle, toCString(filename))

    def withMimeType(mimetype: String)(implicit zone: Zone): CurlCode = CCurl.mimeType(handle, toCString(mimetype))

    def withEncoding(encoding: String)(implicit zone: Zone): CurlCode = CCurl.mimeEncoder(handle, toCString(encoding))

    def withData(data: String, datasize: Int = CurlZeroTerminated)(implicit zone: Zone): CurlCode =
      CCurl.mimeData(handle, toCString(data), datasize: CSize)

    def withFileData(filename: String)(implicit zone: Zone): CurlCode = CCurl.mimeFiledata(handle, toCString(filename))

    def withSubParts(subparts: MimePartHandle): CurlCode = CCurl.mimeSubParts(handle, subparts)

    def withHeaders(headers: Ptr[CurlSlist], takeOwnership: Int = 0): CurlCode =
      CCurl.mimeHeaders(handle, headers, takeOwnership)
  }

  implicit class SlistHandleOps(handle: SlistHandle) {
    def append(string: String)(implicit z: Zone): Ptr[CurlSlist] = {
      CCurl.slistAppend(handle, toCString(string)(z))
    }

    def free(): Unit = {
      CCurl.slistFree(handle)
    }
  }
}
