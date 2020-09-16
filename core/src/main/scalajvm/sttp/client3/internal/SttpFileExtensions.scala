package sttp.client3.internal

import java.nio.file.Files
import java.nio.file.Path

import scala.io.Source

// wrap a Path
trait SttpFileExtensions { self: SttpFile =>

  def toPath: Path = underlying.asInstanceOf[Path]
  def toFile: java.io.File = toPath.toFile

  def readAsString: String = {
    val s = Source.fromFile(toFile, "UTF-8");
    try s.getLines().mkString("\n")
    finally s.close()
  }
}

trait SttpFileCompanionExtensions {
  def fromPath(path: Path): SttpFile =
    new SttpFile(path) {
      val name: String = path.getFileName.toString
      def size: Long = Files.size(path)
    }
  def fromFile(file: java.io.File): SttpFile = fromPath(file.toPath)
}
