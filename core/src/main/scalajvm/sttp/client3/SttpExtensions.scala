package sttp.client3

import java.io.File
import java.nio.file.Path

import sttp.client3.internal.ResponseAsFile
import sttp.client3.internal.SttpFile
import sttp.model.{Part, StatusCode}

trait SttpExtensions {
  def asFile(file: File): ResponseAs[Either[String, File]] = asEither(asStringAlways, asFileAlways(file))

  def asFileAlways(file: File): ResponseAs[File] =
    new ResponseAs(ResponseAsFile(SttpFile.fromFile(file)).map(_.toFile))

  def asPath(path: Path): ResponseAs[Either[String, Path]] = asEither(asStringAlways, asPathAlways(path))

  def asPathAlways(path: Path): ResponseAs[Path] =
    new ResponseAs(ResponseAsFile(SttpFile.fromPath(path)).map(_.toPath))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, data: File): Part[BasicBodyPart] = multipartSttpFile(name, SttpFile.fromFile(data))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipartFile(name: String, data: Path): Part[BasicBody] = multipartSttpFile(name, SttpFile.fromPath(data))
}

object SttpExtensions {

  /** This needs to be platform-specific due to #1682, as on JS we don't get access to the 101 status code.
    * asWebSocketEither delegates to this method, as the method itself cannot be moved, due to binary compatibility.
    */
  private[client3] def asWebSocketEitherPlatform[F[_], A, B](
      onError: ResponseAs[A],
      onSuccess: WebSocketResponseAs[F, B]
  ): WebSocketResponseAs[F, Either[A, B]] =
    fromMetadata(
      onError.map(Left(_)),
      ConditionalResponseAs(_.code == StatusCode.SwitchingProtocols, onSuccess.map(Right(_)))
    ).showAs(s"either(${onError.show}, ${onSuccess.show})")
}
