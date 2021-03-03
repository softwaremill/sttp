package sttp.client3.impl.cats

import cats.effect.IO
import sttp.client3.SttpBackend

import scala.concurrent.ExecutionContext

class FetchCatsHttpTest extends AbstractFetchHttpTest[IO, Any] with CatsTestBase {
  implicit override def executionContext: ExecutionContext = queue

  override val backend: SttpBackend[IO, Any] = FetchCatsBackend()

  override protected def supportsCustomMultipartContentType = false

  override def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] =
    super[CatsTestBase].timeoutToNone(t, timeoutMillis)
}
