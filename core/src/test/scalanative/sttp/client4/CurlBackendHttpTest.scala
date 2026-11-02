package sttp.client4
package curl

import sttp.client4.curl.CurlBackend
import sttp.client4.curl.internal.CCurl
import sttp.client4.testing.SyncHttpTest
import scalanative.unsafe.fromCString

import java.io.InputStream
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
  }

  /** Interrupts the thread sending the request, returning the exception and the time to stop after interrupting. */
  private def interruptWhileSending(request: Request[?]): (Option[Throwable], Long) = {
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

  private def readAll(is: InputStream): Unit = while (is.read() != -1) {}

  // mirrors HttpTest's cancel tests, using thread interruption
  "cancel" - {
    // the response to /timeout arrives after 2 seconds
    "a request before any response is received" in {
      val (e, time) = interruptWhileSending(basicRequest.get(uri"$endpoint/timeout"))
      e.value shouldBe an[InterruptedException]
      time should be < 500L
    }

    "a streamed request before any response is received" in {
      val (e, time) = interruptWhileSending(basicRequest.get(uri"$endpoint/timeout").response(asInputStreamAlways(readAll)))
      e.value shouldBe an[InterruptedException]
      time should be < 500L
    }

    // the response body to /streaming/slow is sent in 20 parts, over 2 seconds
    "a request when the response is produced slowly" in {
      val (e, time) = interruptWhileSending(basicRequest.get(uri"$endpoint/streaming/slow"))
      e.value shouldBe an[InterruptedException]
      time should be < 500L
    }

    "a streamed request when the response is produced slowly" in {
      val (e, time) =
        interruptWhileSending(basicRequest.get(uri"$endpoint/streaming/slow").response(asInputStreamAlways(readAll)))
      e.value shouldBe an[InterruptedException]
      time should be < 500L
    }
  }
}
