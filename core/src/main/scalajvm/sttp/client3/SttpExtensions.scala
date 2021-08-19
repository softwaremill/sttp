package sttp.client3

import java.io.File
import java.nio.file.Path

import sttp.client3.internal.SttpFile
import sttp.model.Part

trait SttpExtensions {
  def asFile(file: File): ResponseAs[Either[String, File], Any] = {
    asEither(asStringAlways, asFileAlways(file))
  }

  def asFileAlways(file: File): ResponseAs[File, Any] = {
    ResponseAsFile(SttpFile.fromFile(file)).map(_.toFile)
  }

  def asPath(path: Path): ResponseAs[Either[String, Path], Any] = {
    asEither(asStringAlways, asPathAlways(path))
  }

  def asPathAlways(path: Path): ResponseAs[Path, Any] = {
    ResponseAsFile(SttpFile.fromPath(path)).map(_.toPath)
  }

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, data: File): Part[RequestBody[Any]] =
    multipartSttpFile(name, SttpFile.fromFile(data))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, data: Path): Part[RequestBody[Any]] =
    multipartSttpFile(name, SttpFile.fromPath(data))
}
