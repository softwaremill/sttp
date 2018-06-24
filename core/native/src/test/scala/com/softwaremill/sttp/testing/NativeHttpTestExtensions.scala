package com.softwaremill.sttp.testing

import java.nio.file.Files
import java.security.MessageDigest

import scala.concurrent.Future
import scala.language.higherKinds
import com.softwaremill.sttp.internal.SttpFile

trait NativeHttpTestExtensions extends AsyncExecutionContext {
  self: NativeHttpTest =>

  override protected def withTemporaryFile[T](content: Option[Array[Byte]])(f: SttpFile => Future[T]): Future[T] = {
    val file = Files.createTempFile("sttp", "sttp")
    val result = Future {
      content match {
        case None       => Files.deleteIfExists(file)
        case Some(data) => Files.write(file, data)
      }
    }.flatMap { _ =>
      f(SttpFile.fromPath(file))
    }

    result.onComplete(_ => Files.deleteIfExists(file))
    result
  }

  override protected def md5Hash(bytes: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes, 0, bytes.length)
    val hash = md.digest()
    hash.map(0xFF & _).map("%02x".format(_)).mkString
  }

  override protected def md5FileHash(file: SttpFile): Future[String] = {
    Future.successful {
      md5Hash(Files.readAllBytes(file.toPath))
    }
  }
}
