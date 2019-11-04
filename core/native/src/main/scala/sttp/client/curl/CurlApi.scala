package sttp.client.curl

import sttp.client.curl.CurlCode.CurlCode
import sttp.client.curl.CurlInfo.CurlInfo
import sttp.client.curl.CurlOption.CurlOption

import scala.scalanative.native.{Ptr, _}

private[client] object CurlApi {
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
  }

  private def setopt(handle: CurlHandle, option: CurlOption, parameter: Any): CurlCode = {
    CurlCode(CCurl.setopt(handle, option.id, parameter))
  }

  private def getInfo(handle: CurlHandle, curlInfo: CurlInfo, parameter: Any): CurlCode = {
    CurlCode(CCurl.getInfo(handle, curlInfo.id, parameter))
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

    def withData(data: String, datasize: Long = CurlZeroTerminated)(implicit zone: Zone): CurlCode =
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
