package sttp.client3.testing

import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AsyncFreeSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.monad.MonadError

import sttp.client3.testing.HttpTest.endpoint

trait CancelTest[F[_], P] extends AsyncFreeSpecLike with Matchers with ToFutureWrapper with BeforeAndAfterAll {

  def backend: SttpBackend[F, P]

  implicit def convertToFuture: ConvertToFuture[F]

  def timeoutToNone[T](t: F[T], timeoutMillis: Int): F[Option[T]]

  "cancel" - {
    "a request in progress" in {
      implicit val monad: MonadError[F] = backend.responseMonad
      import sttp.monad.syntax._

      val req = basicRequest
        .get(uri"$endpoint/timeout")
        .response(asString)

      val now = monad.eval(System.currentTimeMillis())

      convertToFuture.toFuture(
        now
          .flatMap { start =>
            timeoutToNone(req.send(backend), 100)
              .map { r =>
                (System.currentTimeMillis() - start) should be < 2000L
                r shouldBe None
              }
          }
      )
    }
  }
}
