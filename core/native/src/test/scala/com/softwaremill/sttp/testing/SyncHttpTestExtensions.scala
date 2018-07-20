package com.softwaremill.sttp.testing

import java.nio.file.Files
import java.security.MessageDigest

import com.softwaremill.sttp.internal.SttpFile

trait SyncHttpTestExtensions {
  self: SyncHttpTest =>

  override protected def withTemporaryFile[T](content: Option[Array[Byte]])(f: SttpFile => T): T = {
    val file = Files.createTempFile("sttp", "sttp")
    content match {
      case None       => Files.deleteIfExists(file)
      case Some(data) => Files.write(file, data)
    }
    val result = f(SttpFile.fromPath(file))
    Files.deleteIfExists(file)
    result
  }

  override protected def md5Hash(bytes: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes, 0, bytes.length)
    val hash = md.digest()
    hash.map(0xFF & _).map("%02x".format(_)).mkString
  }

  override protected def md5FileHash(file: SttpFile): String = md5Hash(Files.readAllBytes(file.toPath))
}
