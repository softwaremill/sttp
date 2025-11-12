package sttp.client4.impl.fs2

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.client4.testing.{AbstractFetchHttpTest, ConvertToFuture}
import cats.effect.unsafe.implicits.global

class FetchFs2HttpTest extends AbstractFetchHttpTest[IO, Fs2Streams[IO]] {
  override implicit val convertToFuture: ConvertToFuture[IO] = sttp.client4.impl.cats.convertCatsIOToFuture()

  override val backend: StreamBackend[IO, Fs2Streams[IO]] = FetchFs2Backend()

  override protected def supportsCustomMultipartContentType = false

  override protected def supportsCustomMultipartEncoding = false

  override def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] = super.timeoutToNone(t, timeoutMillis)

}
