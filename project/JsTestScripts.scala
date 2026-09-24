import sbt.*

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import java.util.zip.ZipFile

/** Extracts JavaScript files from webjars, so that they can be loaded before the tests run. Replaces
  * sbt-jsdependencies' `jsDependencies`, which isn't available for sbt 2.
  */
object JsTestScripts {
  def extract(classpath: Seq[File], pathInJar: String, targetDir: File): File = {
    val target = targetDir / pathInJar.split('/').last
    val found = classpath.filter(_.getName.endsWith(".jar")).exists { jar =>
      val zip = new ZipFile(jar)
      try
        Option(zip.getEntry(pathInJar)) match {
          case Some(entry) =>
            IO.createDirectory(targetDir)
            val in = zip.getInputStream(entry)
            try Files.copy(in, target.toPath, StandardCopyOption.REPLACE_EXISTING)
            finally in.close()
            true
          case None => false
        }
      finally zip.close()
    }
    if (!found) sys.error(s"Cannot find $pathInJar on the classpath")
    target
  }
}
