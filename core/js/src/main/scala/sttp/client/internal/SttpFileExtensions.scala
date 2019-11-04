package sttp.client.internal

import sttp.client.dom.experimental.{File => DomFile}
import sttp.client.dom.experimental.File

// wrap a DomFile
trait SttpFileExtensions { self: SttpFile =>

  def toDomFile: File = underlying.asInstanceOf[File]
}

trait SttpFileCompanionExtensions {
  def fromDomFile(file: File): SttpFile = new SttpFile(file) {
    val name: String = file.name
    val size: Long = file.size.toLong
  }
}
