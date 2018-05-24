package com.softwaremill.sttp

import java.io.File

import com.softwaremill.sttp.file.{File => sttpFile}

trait sttpExtensions {

  def asFile(file: File, overwrite: Boolean = false): ResponseAs[File, Nothing] = {
    ResponseAsFile(sttpFile.fromFile(file), overwrite).map(_.toFile)
  }

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, file: File): Multipart =
    multipart(name, sttpFile.fromFile(file))

}
