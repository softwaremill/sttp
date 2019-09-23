package sttp.client

import sttp.client.dom.experimental.{File => DomFile}
import sttp.client.internal.SttpFile
import sttp.client.dom.experimental.File
import sttp.client.internal.SttpFile

trait SttpExtensions {

  def asFile(file: File, overwrite: Boolean = false): ResponseAs[Either[String, File], Nothing] = {
    asEither(asStringAlways, ResponseAsFile(SttpFile.fromDomFile(file), overwrite).map(_.toDomFile))
  }

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, file: File): Multipart =
    multipartSttpFile(name, SttpFile.fromDomFile(file))

}
