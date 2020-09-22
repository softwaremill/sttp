package sttp.client.internal

import org.scalajs.dom.File

// wrap a DomFile
trait SttpFileExtensions { self: SttpFile =>

  def toDomFile: File = underlying.asInstanceOf[File]
}

trait SttpFileCompanionExtensions {
  def fromDomFile(file: File): SttpFile =
    new SttpFile(file) {
      val name: String = file.name
      val size: Long = file.size.toLong
    }
}
