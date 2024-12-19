import java.io.File
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes

object FileUtils {
  def listScalaFiles(basePath: File): Seq[Path] = {
    val dirPath = basePath.toPath
    var result = Vector.empty[Path]

    val fileVisitor = new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (file.toString.endsWith(".scala")) {
          result = result :+ file
        }
        FileVisitResult.CONTINUE
      }
    }

    Files.walkFileTree(dirPath, fileVisitor)
    result
  }
}
