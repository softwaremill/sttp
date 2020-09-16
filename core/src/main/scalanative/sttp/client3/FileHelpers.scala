package sttp.client3

import java.io.{File, FileOutputStream, IOException, InputStream}
import java.nio.file.AccessDeniedException

import sttp.client3.internal._

object FileHelpers {
  private[client3] def saveFile(file: File, is: InputStream): File = {
    if (!file.exists()) {
      if (file.getParentFile != null) {
        file.getParentFile.mkdirs()
      }
      try {
        file.createNewFile()
      } catch {
        case e: AccessDeniedException => throw new IOException("Permission denied", e) // aligns SN bahavior with Java
      }
    }

    val os = new FileOutputStream(file)

    transfer(is, os)
    file
  }
}
