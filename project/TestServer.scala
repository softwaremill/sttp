import sbt.*

import java.io.File

/** Runs the http test server in a forked JVM, in the background. Replaces sbt-revolver's `reStart`, which isn't
  * available for sbt 2: each start stops the previously started server (if any), and the server is stopped when sbt
  * exits.
  */
object TestServer {
  private var process: Option[Process] = None

  def start(
      javaHome: Option[File],
      classpath: Seq[File],
      mainClass: String,
      args: Seq[String],
      logFile: File,
      log: Logger
  ): Unit = synchronized {
    stop(log)

    val java = javaHome.map(h => (h / "bin" / "java").getAbsolutePath).getOrElse("java")
    val command = Seq(java, "-cp", classpath.map(_.getAbsolutePath).mkString(File.pathSeparator), mainClass) ++ args
    IO.createDirectory(logFile.getParentFile)
    log.info(s"Starting test server: $mainClass ${args.mkString(" ")}, logging to $logFile")
    val p = new ProcessBuilder(command*).redirectErrorStream(true).redirectOutput(logFile).start()
    process = Some(p)
  }

  def stop(log: Logger): Unit = synchronized {
    process.filter(_.isAlive).foreach { p =>
      log.info("Stopping the previously started test server")
      p.destroy()
      p.waitFor()
    }
    process = None
  }

  sys.addShutdownHook(process.foreach(_.destroy()))
}
