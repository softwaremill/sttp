package sttp.client3

import sttp.client3.internal._

import java.io.{File, FileOutputStream, IOException, InputStream}
import java.nio.file.AccessDeniedException

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

  private[client3] def getFilePath[T, R](response: ResponseAs[T, R]): Option[SttpFile] = {
    response match {
      case MappedResponseAs(raw, g, _) => getFilePath(raw)
      case rfm: ResponseAsFromMetadata[T, _] =>
        rfm.conditions
          .flatMap(c => getFilePath(c.responseAs))
          .headOption
      case ResponseAsFile(file) => Some(file)
      case _                    => None
    }
  }
}
