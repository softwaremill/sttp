package sttp.client4
package curl

import sttp.client4.curl.CurlBackend
import sttp.client4.curl.internal.CCurl
import sttp.client4.testing.SyncHttpTest
import scalanative.unsafe.fromCString

import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import scala.util.Try

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

    /** Runs GCs while the request is sent, returning the longest GC time (in millis) and the response body. */
    def gcWhileSending[T](request: Request[T]): (Long, T) = {
      val body = new AtomicReference[Try[T]]()
      val sender = new Thread(() => body.set(Try(request.send(backend).body)))
      sender.start()
      Thread.sleep(200)
      var maxGcTime = 0L
      while (sender.isAlive) {
        val start = System.nanoTime()
        System.gc()
        maxGcTime = math.max(maxGcTime, System.nanoTime() - start)
        Thread.sleep(50)
      }
      (maxGcTime / 1000000, body.get().get)
    }

    // the response to /timeout arrives after 2 seconds
    "not block garbage collection while waiting for a response" in {
      val (gcTime, body) = gcWhileSending(basicRequest.get(uri"$endpoint/timeout"))
      body shouldBe Right("Done")
      gcTime should be < 50L
    }

    "not block garbage collection while waiting for a streamed response" in {
      val (gcTime, body) = gcWhileSending(
        basicRequest.get(uri"$endpoint/timeout").response(asInputStreamAlways(is => new String(is.readAllBytes())))
      )
      body shouldBe "Done"
      gcTime should be < 50L
    }

    // the response body to /streaming/slow is sent in 20 parts, over 2 seconds
    "not block garbage collection while receiving a response body" in {
      val (gcTime, body) = gcWhileSending(basicRequest.get(uri"$endpoint/streaming/slow"))
      body shouldBe Right("a" * 20)
      gcTime should be < 50L
    }

    "not block garbage collection while receiving a streamed response body" in {
      val (gcTime, body) = gcWhileSending(
        basicRequest
          .get(uri"$endpoint/streaming/slow")
          .response(asInputStreamAlways(is => new String(is.readAllBytes())))
      )
      body shouldBe "a" * 20
      gcTime should be < 50L
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
      val (e, time) =
        interruptWhileSending(basicRequest.get(uri"$endpoint/timeout").response(asInputStreamAlways(readAll)))
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
