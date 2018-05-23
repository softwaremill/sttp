package com.softwaremill.sttp.testing

import scala.concurrent.Future
import scala.language.higherKinds
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

import com.softwaremill.sttp.dom.experimental.FilePropertyBag
import com.softwaremill.sttp.dom.experimental.{File => DomFile}
import com.softwaremill.sttp.file.File

trait HttpTestExtensions[R[_]] { self: HttpTest[R] =>

  protected def withTemporaryFile[T](content: String)(f: File => Future[T]): Future[T] = {
    val file = new DomFile(
      Array(content.asInstanceOf[js.Any]).toJSArray,
      "temp.txt",
      FilePropertyBag(
        `type` = "text/plain"
      )
    )
    f(File.fromDomFile(file))
  }
}
