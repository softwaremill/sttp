package sttp.client4.asynchttpclient.fs2

import cats.effect.IO
import sttp.client4.Backend
import sttp.client4.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client4.impl.cats.{CatsTestBase, TestIODispatcher}

class AsyncHttpClientFs2HttpTest extends AsyncHttpClientHttpTest[IO] with TestIODispatcher with CatsTestBase {
  override val backend: Backend[IO] =
    AsyncHttpClientFs2Backend[IO](dispatcher = dispatcher).unsafeRunSync()
}
