package sttp.client4.internal

import org.scalajs.dom.File

import java.io.FileInputStream
import java.io.InputStream

// wrap a DomFile
trait SttpFileExtensions { self: SttpFile =>

  def toDomFile: File = underlying.asInstanceOf[File]

  def readAsString(): String = throw new UnsupportedOperationException()
  def readAsByteArray(): Array[Byte] = throw new UnsupportedOperationException()
  def openStream(): InputStream = throw new UnsupportedOperationException()
  def length(): Long = throw new UnsupportedOperationException()
}

trait SttpFileCompanionExtensions {
  def fromDomFile(file: File): SttpFile =
    new SttpFile(file) {
      val name: String = file.name
      val size: Long = file.size.toLong
    }
}
