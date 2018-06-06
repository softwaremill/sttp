package com.softwaremill.sttp.internal

import com.softwaremill.sttp.dom.experimental.{File => DomFile}

// wrap a DomFile
trait SttpFileExtensions { self: SttpFile =>

  def toDomFile: DomFile = underlying.asInstanceOf[DomFile]
}

trait SttpFileCompanionExtensions {

  def fromDomFile(file: DomFile): SttpFile = new SttpFile(file) {
    val name: String = file.name
    val size: Long = file.size.toLong
  }

}
