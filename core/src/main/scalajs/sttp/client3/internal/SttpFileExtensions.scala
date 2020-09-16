package sttp.client3.internal

import sttp.client3.dom.experimental.{File => DomFile}
import sttp.client3.dom.experimental.File

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
