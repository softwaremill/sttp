package sttp.client3.curl

import sttp.client3.curl.CurlCode.CurlCode
import sttp.client3.curl.CurlInfo.CurlInfo
import sttp.client3.curl.CurlOption.CurlOption

import scala.scalanative.runtime.Boxes
import scala.scalanative.unsafe.{Ptr, _}

private[client3] object CurlApi {
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
      setopt(handle, option, toCString(parameter))
    }

    def option(option: CurlOption, parameter: Long)(implicit z: Zone): CurlCode = {
      setopt(handle, option, parameter)
    }

    def option(option: CurlOption, parameter: Int)(implicit z: Zone): CurlCode = {
      setopt(handle, option, parameter)
    }

    def option(option: CurlOption, parameter: Boolean)(implicit z: Zone): CurlCode = {
      setopt(handle, option, if (parameter) 1L else 0L)
    }

    def option(option: CurlOption, parameter: Ptr[_]): CurlCode = {
      setopt(handle, option, parameter)
    }

    def option[FuncPtr <: CFuncPtr](option: CurlOption, parameter: FuncPtr)(implicit z: Zone): CurlCode = {
      setopt(handle, option, Boxes.boxToPtr[Byte](Boxes.unboxToCFuncRawPtr(parameter)))
    }

    def info(curlInfo: CurlInfo, parameter: Long)(implicit z: Zone): CurlCode = {
      val lPtr = alloc[Long](sizeof[Long])
      !lPtr = parameter
      getInfo(handle, curlInfo, lPtr)
    }

    def info(curlInfo: CurlInfo, parameter: String)(implicit z: Zone): CurlCode = {
      getInfo(handle, curlInfo, toCString(parameter))
    }

    def info(curlInfo: CurlInfo, parameter: Ptr[_]): CurlCode = {
      getInfo(handle, curlInfo, parameter)
    }
  }

  private def setopt(handle: CurlHandle, option: CurlOption, parameter: Ptr[_]): CurlCode = {
    CurlCode(CCurl.setopt(handle, option.id, parameter))
  }

  private def setopt(handle: CurlHandle, option: CurlOption, parameter: CVarArg)(implicit z: Zone): CurlCode = {
    CurlCode(CCurl.setopt(handle, option.id, toCVarArgList(Seq(parameter))))
  }

  private def getInfo(handle: CurlHandle, curlInfo: CurlInfo, parameter: Ptr[_]): CurlCode = {
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
