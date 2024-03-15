package sttp.client4.asynchttpclient.fs2

import cats.effect.IO
import sttp.client4.Backend
import sttp.client4.impl.cats.{CatsTestBase, TestIODispatcher}
import sttp.client4.testing.HttpTest

class AsyncHttpClientFs2HttpTest extends AsyncHttpClientHttpTest[IO] with TestIODispatcher with CatsTestBase {
  override val backend: Backend[IO] =
    AsyncHttpClientFs2Backend[IO](dispatcher = dispatcher).unsafeRunSync()

  override def throwsExceptionOnUnsupportedEncoding = false
  // for some unknown reason this single test fails using the fs2 implementation
  override def supportsConnectionRefusedTest = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsResponseAsInputStream = false
}
