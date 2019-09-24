package sttp.client

import java.io.{File, FileOutputStream, IOException, InputStream}
import java.nio.file.AccessDeniedException

import sttp.client.internal._

object FileHelpers {

  private[client] def saveFile(file: File, is: InputStream, overwrite: Boolean): File = {
    if (!file.exists()) {
      if (file.getParentFile != null) {
        file.getParentFile.mkdirs()
      }
      try {
        file.createNewFile()
      } catch {
        case e: AccessDeniedException => throw new IOException("Permission denied", e) // aligns SN bahavior with Java
      }
    } else if (!overwrite) {
      throw new IOException(s"File ${file.getAbsolutePath} exists - overwriting prohibited")
    }

    val os = new FileOutputStream(file)

    transfer(is, os)
    file
  }

}
