package sttp.client

import java.io.File
import java.nio.file.Path

import sttp.client.internal.SttpFile

trait SttpExtensions {

  def asFile(file: File, overwrite: Boolean = false): ResponseAs[Either[String, File], Nothing] = {
    asEither(asStringAlways, ResponseAsFile(SttpFile.fromFile(file), overwrite).map(_.toFile))
  }

  def asPath(path: Path, overwrite: Boolean = false): ResponseAs[Either[String, Path], Nothing] = {
    asEither(asStringAlways, ResponseAsFile(SttpFile.fromPath(path), overwrite).map(_.toPath))
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
