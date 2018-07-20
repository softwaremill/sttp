package com.softwaremill.sttp

import com.softwaremill.sttp.internal._
import java.io.{File, FileOutputStream, IOException, InputStream}

object FileHelpers {

  private[sttp] def saveFile(file: File, is: InputStream, overwrite: Boolean): File = {
    if (!file.exists()) {
      if (file.getParentFile != null) {
        file.getParentFile.mkdirs()
      }
      file.createNewFile()
    } else if (!overwrite) {
      throw new IOException(s"File ${file.getAbsolutePath} exists - overwriting prohibited")
    }

    val os = new FileOutputStream(file)

    transfer(is, os)
    file
  }

}
