package sttp.client3

import org.scalajs.dom.File
import sttp.client3.internal.SttpFile
import sttp.model.{Part, StatusCode}

trait SttpExtensions {
  def asFile(file: File): ResponseAs[Either[String, File], Any] = {
    asEither(asStringAlways, asFileAlways(file))
  }

  def asFileAlways(file: File): ResponseAs[File, Any] = {
    ResponseAsFile(SttpFile.fromDomFile(file)).map(_.toDomFile)
  }

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, file: File): Part[BasicRequestBody] =
    multipartSttpFile(name, SttpFile.fromDomFile(file))
}

object SttpExtensions {

  /** This needs to be platform-specific due to #1682, as on JS we don't get access to the 101 status code.
    * asWebSocketEither delegates to this method, as the method itself cannot be moved, due to binary compatibility.
    */
  private[client3] def asWebSocketEitherPlatform[A, B, R](
      onError: ResponseAs[A, R],
      onSuccess: ResponseAs[B, R]
  ): ResponseAs[Either[A, B], R] =
    fromMetadata(
      onError.map(Left(_)),
      ConditionalResponseAs(_.code == StatusCode.Ok, onSuccess.map(Right(_)))
    ).showAs(s"either(${onError.show}, ${onSuccess.show})")
}
