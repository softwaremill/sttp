package com.softwaremill.sttp

import java.io.File
import java.nio.file.Path

import com.softwaremill.sttp.internal.SttpFile

trait SttpExtensions2 {

  def asFile(file: File, overwrite: Boolean = false): ResponseAs[File, Nothing] = {
    ResponseAsFile(SttpFile.fromFile(file), overwrite).map(_.toFile)
  }

  def asPath(path: Path, overwrite: Boolean = false): ResponseAs[Path, Nothing] = {
    ResponseAsFile(SttpFile.fromPath(path), overwrite).map(_.toPath)
  }

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, data: File): Multipart =
    multipartSttpFile(name, SttpFile.fromFile(data))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, data: Path): Multipart =
    multipartSttpFile(name, SttpFile.fromPath(data))
}
