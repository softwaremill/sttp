package sttp.client4
package curl

import sttp.client4.curl.CurlBackend
import sttp.client4.curl.internal.CCurl
import sttp.client4.testing.SyncHttpTest
import scalanative.unsafe.fromCString

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

    // the response to /timeout arrives after 2 seconds
    def gcTimeWhileWaitingForResponse(request: Request[?]): Long = {
      val sender = new Thread(() => { val _ = request.send(backend) })
      sender.start()
      Thread.sleep(500)
      val start = System.currentTimeMillis()
      System.gc()
      val gcTime = System.currentTimeMillis() - start
      sender.join()
      gcTime
    }

    "not block garbage collection while waiting for a response" in {
      gcTimeWhileWaitingForResponse(basicRequest.get(uri"$endpoint/timeout")) should be < 500L
    }

    "not block garbage collection while waiting for a streamed response" in {
      gcTimeWhileWaitingForResponse(
        basicRequest.get(uri"$endpoint/timeout").response(asInputStreamAlways(_ => ()))
      ) should be < 500L
    }
  }
}
