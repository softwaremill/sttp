package sttp.client3

import org.scalajs.dom.File
import sttp.client3.internal.SttpFile
import sttp.model.Part

trait SttpExtensions {
  def asFile(file: File): ResponseAs[Either[String, File], Any] = {
    asEither(asStringAlways, asFileAlways(file))
  }

  def asFileAlways(file: File): ResponseAs[File, Any] = {
    ResponseAsFile(SttpFile.fromDomFile(file)).map(_.toDomFile)
  }

  /** Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, file: File): Part[BasicRequestBody] =
    multipartSttpFile(name, SttpFile.fromDomFile(file))
}
