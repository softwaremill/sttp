package com.softwaremill.sttp

import com.softwaremill.sttp.dom.experimental.File
import com.softwaremill.sttp.internal.SttpFile

import scala.language.higherKinds

trait RequestTExtensions[U[_], T, +S] { self: RequestT[U, T, S] =>

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  def body(file: File): RequestT[U, T, S] = body(SttpFile.fromDomFile(file))
}
