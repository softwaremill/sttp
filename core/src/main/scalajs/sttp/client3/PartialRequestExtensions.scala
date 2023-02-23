package sttp.client3

import sttp.client3.internal.SttpFile
import org.scalajs.dom.File

trait PartialRequestExtensions[+R <: PartialRequestBuilder[R, _]] { self: R =>

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given file.
    */
  def body(file: File): R = body(SttpFile.fromDomFile(file))

  // this method needs to be in the extensions, so that it has lowest priority when considering overloading options
  /** If content type is not yet specified, will be set to `application/octet-stream`.
    */
  def body[B: BodySerializer](b: B): R =
    withBody(implicitly[BodySerializer[B]].apply(b))
}
