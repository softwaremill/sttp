package sttp.client3.dom.experimental

import org.scalajs.dom.raw.Blob

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

// the File interface in scala.js does not match the spec
// https://developer.mozilla.org/en-US/docs/Web/API/File

@js.native
@JSGlobal
class File(
    parts: js.Array[js.Any] = js.native,
    val name: String = js.native,
    options: FilePropertyBag = js.native
) extends Blob {
  val lastModified: Int = js.native
}

@js.native
@JSGlobal
object File extends js.Object

@js.native
trait FilePropertyBag extends js.Object {
  def `type`: String = js.native

  def lastModified: Int = js.native
}

object FilePropertyBag {
  @inline
  def apply(
      `type`: js.UndefOr[String] = js.undefined,
      lastModified: js.UndefOr[Int] = js.undefined
  ): FilePropertyBag = {
    val result = js.Dynamic.literal()
    `type`.foreach(result.`type` = _)
    lastModified.foreach(result.lastModified = _)
    result.asInstanceOf[FilePropertyBag]
  }
}
