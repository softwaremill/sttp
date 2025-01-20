package sttp.client4

import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.httpclient.RequestBodyProgressCallback
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.HttpTest
import sttp.model.StatusCode
import sttp.shared.Identity

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._

class HttpClientSyncHttpTest extends HttpTest[Identity] {
  override val backend: WebSocketSyncBackend = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsHostHeaderOverride = false
  override def supportsCancellation: Boolean = false
  override def supportsDeflateWrapperChecking = false

  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)

  "callback" - {
    "should be invoked as described in the callback protocol" in {
      val trail = new ConcurrentLinkedQueue[String]()
      val callback = new RequestBodyProgressCallback {

        override def onInit(contentLength: Option[Long]): Unit = {
          val _ = trail.add(s"init ${contentLength.getOrElse(-1)}")
        }

        override def onNext(bytesCount: Long): Unit = {
          val _ = trail.add(s"next $bytesCount")
        }

        override def onComplete(): Unit = {
          val _ = trail.add(s"complete")
        }

        override def onError(t: Throwable): Unit = {
          val _ = trail.add(s"error")
        }
      }

      val contentLength = 2048 * 100
      val req = postEcho.body("x" * contentLength).attribute(RequestBodyProgressCallback.Attribute, callback)

      (req.send(backend): Identity[Response[Either[String, String]]]).toFuture().map { response =>
        val t = trail.asScala

        t.size should be >= 3
        t.head shouldBe s"init $contentLength"
        t.tail.init.foreach(_ should startWith("next "))
        t.last shouldBe "complete"
        response.code shouldBe StatusCode.Ok
      }
    }
  }
}
