package sttp.client4

import sttp.client4.internal.SttpFile
import org.scalajs.dom.File

trait PartialRequestExtensions[+R <: PartialRequestBuilder[R, _]] { self: R =>

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given file.
    */
  def body(file: File): R = body(SttpFile.fromDomFile(file))
}
