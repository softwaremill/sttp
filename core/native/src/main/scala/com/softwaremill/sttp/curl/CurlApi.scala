package com.softwaremill.sttp.curl

import com.softwaremill.sttp.curl.CurlCode.CurlCode
import com.softwaremill.sttp.curl.CurlInfo.CurlInfo
import com.softwaremill.sttp.curl.CurlOption.CurlOption

import scala.scalanative.native
import scala.scalanative.native.{Ptr, _}

private[sttp] object CurlApi {

  type CurlHandle = Ptr[CURL]

  def init: CurlHandle = CCurl.init

  private def setopt(handle: CurlHandle, option: CurlOption, parameter: Any): CurlCode = {
    CurlCode(CCurl.setopt(handle, option.id, parameter))
  }

  private def getInfo(handle: CurlHandle, curlInfo: CurlInfo, parameter: Any): CurlCode = {
    CurlCode(CCurl.getInfo(handle, curlInfo.id, parameter))
  }

  implicit class CurlHandleOps(handle: CurlHandle) {
    def perform: CurlCode = CurlCode(CCurl.perform(handle))

    def cleanup(): Unit = CCurl.cleanup(handle)

    def option(option: CurlOption, parameter: String): CurlCode = native.Zone { implicit z =>
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

  type SlistHandle = Ptr[CurlSlist]

  def slistAppend(handle: SlistHandle, string: String): SlistHandle = native.Zone { implicit z =>
    CCurl.slistAppend(handle, toCString(string)(z))
  }

  def slistFree(handle: SlistHandle): Unit = {
    CCurl.slistFree(handle)
  }
}
