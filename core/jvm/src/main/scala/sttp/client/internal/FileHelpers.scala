package sttp.client.internal

import java.io.{File, FileOutputStream, InputStream}

private[client] object FileHelpers {

  def saveFile(file: File, is: InputStream): Unit = {
    if (!file.exists()) {
      if (file.getParentFile != null) {
        file.getParentFile.mkdirs()
      }
      file.createNewFile()
    }

    val os = new FileOutputStream(file)

    transfer(is, os)
  }

}
