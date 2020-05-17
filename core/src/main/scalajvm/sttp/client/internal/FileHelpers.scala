package sttp.client.internal

import java.io.{File, FileOutputStream, InputStream}

import scala.util.control.NonFatal

private[client] object FileHelpers {
  def saveFile(file: File, is: InputStream): Unit = {
    if (!file.exists()) {
      if (file.getParentFile != null) {
        file.getParentFile.mkdirs()
      }
      file.createNewFile()
    }

    withResources(new FileOutputStream(file))(os => transfer(is, os))
  }

  // based on https://medium.com/@dkomanov/scala-try-with-resources-735baad0fd7d
  private def withResources[T <: AutoCloseable, V](r: => T)(f: T => V): V = {
    val resource: T = r
    require(resource != null, "resource is null")
    var exception: Throwable = null
    try {
      f(resource)
    } catch {
      case NonFatal(e) =>
        exception = e
        throw e
    } finally {
      closeAndAddSuppressed(exception, resource)
    }
  }

  private def closeAndAddSuppressed(e: Throwable, resource: AutoCloseable): Unit = {
    if (e != null) {
      try {
        resource.close()
      } catch {
        case NonFatal(suppressed) =>
          e.addSuppressed(suppressed)
      }
    } else {
      resource.close()
    }
  }
}
