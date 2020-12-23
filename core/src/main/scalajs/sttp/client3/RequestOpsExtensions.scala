package sttp.client3

import sttp.client3.internal.SttpFile
import org.scalajs.dom.File

import scala.language.higherKinds

trait RequestOpsExtensions[T, -R] { self: RequestOps[T, R] =>

  /** If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  def body(file: File): ThisType[T, R] = body(SttpFile.fromDomFile(file))

  // this method needs to be in the extensions, so that it has lowest priority when considering overloading options
  /** If content type is not yet specified, will be set to
    * `application/octet-stream`.
    */
  def body[B: BodySerializer](b: B): ThisType[T, R] =
    withBodySetContentTypeIfMissing[R](implicitly[BodySerializer[B]].apply(b))
}
