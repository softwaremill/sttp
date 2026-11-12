package sttp.client4
package curl

import sttp.client4.curl.CurlBackend
import sttp.client4.curl.internal.CCurl
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import sttp.client4.testing.HttpTest.endpoint
import sttp.shared.Identity
import org.scalatest.Assertion
import scalanative.unsafe.fromCString

import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.util.Try

class CurlBackendHttpTest extends HttpTest[Identity] {
  override val backend: SyncBackend = CurlBackend(verbose = true)
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsHostHeaderOverride = false
  override def supportsDeflateWrapperChecking = false
  // covered by the thread-interruption tests below
  override def supportsCancellation = false
  override def supportsMaxResponseBodyLength = false

  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)

  "curl backend" - {
    "include libcurl's error description in transfer errors" - {
      val CurlErrorMessage = """curl error (\d+) \((\w+)\): (.+)""".r

      /** Checks that the detail comes from the request's error buffer, not the generic fallback for the code. */
      def checkRequestSpecificDetail(e: Throwable): String = e.getMessage match {
        case CurlErrorMessage(code, _, detail) =>
          detail should not be fromCString(CCurl.easyStrerror(code.toInt))
          detail
        case msg => fail(s"Unexpected message: $msg")
      }

      def checkConnectionRefused(request: Request[?]): Assertion = {
        val e = intercept[SttpClientException.ConnectException](request.send(backend))
        e.getCause.getMessage should startWith("curl error 7 (COULDNT_CONNECT): ")
        checkRequestSpecificDetail(e.getCause) should include("7918")
      }

      "when buffering the response" in {
        checkConnectionRefused(basicRequest.get(uri"http://localhost:7918"))
      }

      "when streaming the response" in {
        checkConnectionRefused(
          basicRequest.get(uri"http://localhost:7918").response(asInputStreamAlways(is => new String(is.readAllBytes())))
        )
      }

      "when saving the response to a file" in {
        val file = java.io.File.createTempFile("sttp", "curl")
        try checkConnectionRefused(basicRequest.get(uri"http://localhost:7918").response(asFile(file)))
        finally { val _ = file.delete() }
      }

      // the server sends the headers and one byte, then stalls until the transfer times out
      "when the transfer fails while buffering a response which might be streamed" in {
        val request = basicRequest
          .get(uri"$endpoint/streaming/stall")
          .readTimeout(500.millis)
          .response(asEither(asInputStreamAlwaysUnsafe, asStringAlways)) // 200 selects the buffered branch
        val e = intercept[SttpClientException.TimeoutException](request.send(backend))
        e.getCause.getMessage should startWith("curl error 28 (OPERATION_TIMEDOUT): ")
        val _ = checkRequestSpecificDetail(e.getCause)
        succeed
      }

      "when the transfer fails while reading a streamed response" in {
        val is = basicRequest
          .get(uri"$endpoint/streaming/stall")
          .readTimeout(500.millis)
          .response(asInputStreamAlwaysUnsafe)
          .send(backend)
          .body
        try {
          is.read() shouldBe 'a'.toInt
          val e = intercept[SocketTimeoutException](is.read())
          e.getMessage should startWith("curl error 28 (OPERATION_TIMEDOUT): ")
          val _ = checkRequestSpecificDetail(e)
          succeed
        } finally is.close()
      }
    }

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
