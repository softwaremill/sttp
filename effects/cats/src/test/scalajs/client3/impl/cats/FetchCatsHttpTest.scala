package sttp.client3.impl.cats

import cats.effect.IO
import sttp.client3.testing.AbstractFetchHttpTest
import sttp.client3.{ConvertFromFuture, SttpBackend}

import scala.concurrent.Future

class FetchCatsHttpTest extends AbstractFetchHttpTest[IO, Any] with CatsTestBase {

  val convertFromFuture: ConvertFromFuture[IO] = new ConvertFromFuture[IO] {
    override def fromFuture[T](f: Future[T]): IO[T] = IO.fromFuture(IO(f))
  }

  override val backend: SttpBackend[IO, Any] = FetchCatsBackend(convertFromFuture = convertFromFuture)

  override protected def supportsCustomMultipartContentType = false

  override def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] =
    super[CatsTestBase].timeoutToNone(t, timeoutMillis)
}
