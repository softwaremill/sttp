package sttp.client3.testing

import org.scalajs.dom.{Blob, FileReader}
import org.scalajs.dom.raw.{Event, UIEvent}
import sttp.client3._
import sttp.client3.dom.experimental.{FilePropertyBag, File => DomFileWithBody}
import sttp.client3.internal.SparkMD5

import scala.concurrent.{Future, Promise}
import scala.language.higherKinds
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.JavaScriptException
import scala.scalajs.js.typedarray.AB2TA
import org.scalatest.freespec.AsyncFreeSpecLike
import HttpTest.endpoint

trait HttpTestExtensions[F[_]] extends AsyncFreeSpecLike with AsyncExecutionContext { self: HttpTest[F] =>

  private def withTemporaryFile[T](content: Option[Array[Byte]])(f: DomFileWithBody => Future[T]): Future[T] = {
    val data = content.getOrElse(Array.empty[Byte])
    val file = new DomFileWithBody(
      Array(data.toTypedArray.asInstanceOf[js.Any]).toJSArray,
      "temp.txt",
      FilePropertyBag(
        `type` = "text/plain"
      )
    )
    f(file)
  }

  private def withTemporaryNonExistentFile[T](f: DomFileWithBody => Future[T]): Future[T] = withTemporaryFile(None)(f)

  private def md5Hash(blob: Blob): Future[String] = {
    val p = Promise[String]()

    val fileReader = new FileReader()
    fileReader.onload = (_: UIEvent) => {
      val arrayBuffer = fileReader.result.asInstanceOf[scala.scalajs.js.typedarray.ArrayBuffer]
      val hash = SparkMD5.ArrayBuffer.hash(arrayBuffer)
      p.success(hash)
    }
    fileReader.onerror = (_: Event) => p.failure(JavaScriptException("Error reading file"))
    fileReader.onabort = (_: Event) => p.failure(JavaScriptException("File read aborted"))
    fileReader.readAsArrayBuffer(blob)

    p.future
  }

  "body" - {
    "post a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        postEcho.body(f).send(backend).toFuture().map { response =>
          response.body should be(Right(expectedPostEchoResponse))
        }
      }
    }
  }

  "download file" - {
    "download a binary file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        req.send(backend).toFuture().flatMap { resp =>
          md5Hash(resp.body.right.get).map { _ shouldBe binaryFileMD5Hash }
        }
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/text").response(asFile(file))
        req.send(backend).toFuture().flatMap { resp =>
          md5Hash(resp.body.right.get).map { _ shouldBe textFileMD5Hash }
        }
      }
    }
  }

  "multipart" - {
    def mp = basicRequest.post(uri"$endpoint/multipart")

    "send a multipart message with a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        val req = mp.multipartBody(multipartFile("p1", f), multipart("p2", "v2"))
        req.send(backend).toFuture().map { resp =>
          resp.body should be(Right(s"p1=$testBody (${f.name}), p2=v2$defaultFileName"))
        }
      }
    }

    "throw an exception when trying to send a multipart message with an unsupported content type" in {
      val req = basicRequest
        .post(uri"$endpoint/multipart/other")
        .response(asStringAlways)
        .multipartBody(multipart("p1", "v1"))
        .contentType("multipart/mixed")
      assertThrows[IllegalArgumentException](
        req.send(backend).toFuture()
      )
    }
  }
}
