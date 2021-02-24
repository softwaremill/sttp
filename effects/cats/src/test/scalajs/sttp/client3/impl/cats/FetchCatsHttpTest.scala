package sttp.client3.impl.cats

import cats.effect.IO
import sttp.client3.SttpBackend
import sttp.client3.testing.AbstractFetchHttpTest

class FetchCatsHttpTest extends AbstractFetchHttpTest[IO, Any] with CatsTestBase {
  override val backend: SttpBackend[IO, Any] = FetchCatsBackend(convertFromFuture = convertFromFuture)

  override protected def supportsCustomMultipartContentType = false

  override def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] =
    super[CatsTestBase].timeoutToNone(t, timeoutMillis)
}
