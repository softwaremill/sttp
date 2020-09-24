package sttp.client3.internal

import org.scalajs.dom.File

// wrap a DomFile
trait SttpFileExtensions { self: SttpFile =>

  def toDomFile: File = underlying.asInstanceOf[File]

  def readAsString: String = throw new UnsupportedOperationException()
}

trait SttpFileCompanionExtensions {
  def fromDomFile(file: File): SttpFile =
    new SttpFile(file) {
      val name: String = file.name
      val size: Long = file.size.toLong
    }
}
