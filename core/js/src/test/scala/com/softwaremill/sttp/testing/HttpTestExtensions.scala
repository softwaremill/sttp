package com.softwaremill.sttp.testing

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.language.higherKinds
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.JavaScriptException
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.AB2TA

import com.softwaremill.sttp.dom.experimental.FilePropertyBag
import com.softwaremill.sttp.dom.experimental.{File => DomFile}
import com.softwaremill.sttp.file.File
import org.scalajs.dom.FileReader
import org.scalajs.dom.raw.Event
import org.scalajs.dom.raw.UIEvent

trait HttpTestExtensions[R[_]] { self: HttpTest[R] =>

  override protected def withTemporaryFile[T](content: Option[Array[Byte]])(f: File => Future[T]): Future[T] = {
    val data = content.getOrElse(Array.empty)
    val file = new DomFile(
      Array(data.toTypedArray.asInstanceOf[js.Any]).toJSArray,
      "temp.txt",
      FilePropertyBag(
        `type` = "text/plain"
      )
    )
    f(file)
  }

  override protected def md5Hash(bytes: Array[Byte]): String = {
    SparkMD5.ArrayBuffer.hash(bytes.toTypedArray.buffer)
  }

  override protected def md5FileHash(file: File): Future[String] = {
    val p = Promise[String]()

    val fileReader = new FileReader()
    fileReader.onload = (_: UIEvent) => {
      val arrayBuffer = fileReader.result.asInstanceOf[scala.scalajs.js.typedarray.ArrayBuffer]
      val hash = SparkMD5.ArrayBuffer.hash(arrayBuffer)
      p.success(hash)
    }
    fileReader.onerror = (_: Event) => p.failure(JavaScriptException("Error reading file"))
    fileReader.onabort = (_: Event) => p.failure(JavaScriptException("File read aborted"))
    fileReader.readAsArrayBuffer(file.toDomFile)

    p.future
  }
}

@js.native
@JSGlobal
object SparkMD5 extends js.Object {

  @js.native
  object ArrayBuffer extends js.Object {
    def hash(arr: scala.scalajs.js.typedarray.ArrayBuffer, raw: Boolean = false): String = js.native
  }

  @js.native
  class ArrayBuffer extends js.Object {
    def append(arr: scala.scalajs.js.typedarray.ArrayBuffer): Unit = js.native
    def end(raw: Boolean = false): String = js.native
  }

}
