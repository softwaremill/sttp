// {cat=Resilience; effects=Direct; backend=HttpClient}: Rate limit sending requests using Ox

//> using dep com.softwaremill.sttp.client4::ox:4.0.0-RC3

package sttp.client4.examples.resilience

import ox.Ox
import ox.OxApp
import sttp.client4.*

import scala.concurrent.duration.*
import ox.resilience.RateLimiter
import java.time.Instant

object RateLimitOx extends OxApp.Simple:
  override def run(using Ox): Unit =
    val backend = DefaultSyncBackend()

    val rateLimiter = RateLimiter.fixedWindow(1, 3.seconds)
    for (_ <- 1 to 5)
      rateLimiter.runBlocking {
        println(s"${Instant.now()} Sending request ...")
        basicRequest.get(uri"https://httpbin.org/status/500").send(backend)
      }
