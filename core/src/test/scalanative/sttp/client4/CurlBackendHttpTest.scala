package sttp.client4
package curl

import sttp.client4.curl.CurlBackend
import sttp.client4.curl.internal.CCurl
import sttp.client4.testing.SyncHttpTest
import scalanative.unsafe.fromCString

import java.util.concurrent.atomic.AtomicReference

class CurlBackendHttpTest extends SyncHttpTest {
  override implicit val backend: SyncBackend = CurlBackend(verbose = true)
  "curl backend" - {
    "set user agent in requests" in {
      val response = basicRequest
        .get(uri"$endpoint/echo/headers")
        .send(backend)

      val requestUserAgent = response.body
        .fold(sys.error(_), identity)
        .split(",")
        .map(_.toLowerCase)
        .find(_.startsWith("user-agent->"))
        .map(_.stripPrefix("user-agent->"))

      requestUserAgent should be(Some(s"sttp-curl/${fromCString(CCurl.getVersion())}"))
    }

    // the response to /timeout arrives after 2 seconds; returns the exception and the time to stop after interrupting
    def interruptWhileWaitingForResponse(request: Request[?]): (Option[Throwable], Long) = {
      val exception = new AtomicReference[Throwable]()
      val sender = new Thread(() =>
        try { val _ = request.send(backend) }
        catch { case e: Throwable => exception.set(e) }
      )
      sender.start()
      Thread.sleep(500)
      val start = System.currentTimeMillis()
      sender.interrupt()
      sender.join()
      (Option(exception.get()), System.currentTimeMillis() - start)
    }

    "cancel a request when the sending thread is interrupted" in {
      val (e, time) = interruptWhileWaitingForResponse(basicRequest.get(uri"$endpoint/timeout"))
      e.value shouldBe an[InterruptedException]
      time should be < 500L
    }

    "cancel a streamed request when the sending thread is interrupted" in {
      val (e, time) = interruptWhileWaitingForResponse(
        basicRequest.get(uri"$endpoint/timeout").response(asInputStreamAlways(_ => ()))
      )
      e.value shouldBe an[InterruptedException]
      time should be < 500L
    }
  }
}
