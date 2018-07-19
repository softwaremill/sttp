package com.softwaremill.sttp

import com.softwaremill.sttp.dom.experimental.{File => DomFile}
import com.softwaremill.sttp.internal.SttpFile

trait SttpExtensions {

  def asFile(file: DomFile, overwrite: Boolean = false): ResponseAs[DomFile, Nothing] = {
    ResponseAsFile(SttpFile.fromDomFile(file), overwrite).map(_.toDomFile)
  }

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, file: DomFile): Multipart =
    multipartSttpFile(name, SttpFile.fromDomFile(file))

}
