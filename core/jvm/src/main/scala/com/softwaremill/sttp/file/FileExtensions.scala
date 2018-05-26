package com.softwaremill.sttp.file

import java.nio.file.Files
import java.nio.file.Path

// wrap a Path
trait FileExtensions { self: File =>

  def toPath: Path = underlying.asInstanceOf[Path]
  def toFile: java.io.File = toPath.toFile
}

trait FileObjectExtensions {

  def fromPath(path: Path): File = new File(path) {
    val name: String = path.getFileName.toString
    def size: Long = Files.size(path)
  }
  def fromFile(file: java.io.File): File = fromPath(file.toPath)

}
