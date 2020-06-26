package sttp.client

import sttp.client.internal.SttpFile
import sttp.client.dom.experimental.File

import scala.language.higherKinds

trait RequestTExtensions[U[_], T, -R] { self: RequestT[U, T, R] =>

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  def body(file: File): RequestT[U, T, R] = body(SttpFile.fromDomFile(file))

  // this method needs to be in the extensions, so that it has lowest priority when considering overloading options
  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    */
  def body[B: BodySerializer](b: B): RequestT[U, T, R] =
    withBasicBody(implicitly[BodySerializer[B]].apply(b))
}
