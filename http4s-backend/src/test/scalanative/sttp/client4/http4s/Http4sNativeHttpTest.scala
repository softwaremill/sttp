package sttp.client4.http4s

import cats.effect.IO
import sttp.client4.Backend
import sttp.client4.impl.cats.{CatsRetryTest, CatsTestBase}
import sttp.client4.testing.HttpTest

class Http4sNativeHttpTest extends HttpTest[IO] with CatsRetryTest with CatsTestBase {

  override val backend: Backend[IO] = {
    try {
      Http4sBackend.usingDefaultEmberClientBuilder[IO]().allocated.unsafeRunSync()._1
    } catch {
      case e: Throwable =>
        Console.err.println("[Http4sNativeHttpTest] Failed to create backend:")
        e.printStackTrace(Console.err)
        throw e
    }
  }

  override protected def supportsRequestTimeout = false
  override protected def supportsCustomMultipartContentType = false
}
