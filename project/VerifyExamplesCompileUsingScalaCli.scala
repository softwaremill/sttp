import java.io.File
import sbt.Logger
import scala.sys.process.{Process, ProcessLogger}

object VerifyExamplesCompileUsingScalaCli {
  def apply(log: Logger, examplesSrcPath: File): Unit = {
    val examples = FileUtils.listScalaFiles(examplesSrcPath)
    log.info(s"Found ${examples.size} examples")

    for (example <- examples) {
      log.info(s"Compiling: $example")
      val errorOutput = new StringBuilder
      val logger = ProcessLogger((o: String) => (), (e: String) => errorOutput.append(e + "\n"))
      try {
        val result = Process(List("scala-cli", "compile", example.toFile.getAbsolutePath), examplesSrcPath).!(logger)
        if (result != 0) {
          throw new Exception(s"""Compiling $example failed.\n$errorOutput""".stripMargin)
        }
      } finally {
        Process(List("scala-cli", "clean", example.toFile.getAbsolutePath), examplesSrcPath).!
      }
    }
  }
}
