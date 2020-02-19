package sttp.client.testing

import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.client.monad.MonadError

import scala.language.higherKinds

trait CancelTest[F[_], S] extends AsyncFreeSpec with Matchers with ToFutureWrapper with BeforeAndAfterAll {

  protected def endpoint: String

  implicit def backend: SttpBackend[F, S, NothingT]

  implicit def convertToFuture: ConvertToFuture[F]

  def timeoutToNone[T](t: F[T], timeoutMillis: Int): F[Option[T]]

  "cancel" - {
    "a request in progress" in {
      implicit val monad: MonadError[F] = backend.responseMonad
      import sttp.client.monad.syntax._

      val req = basicRequest
        .get(uri"$endpoint/timeout")
        .response(asString)

      val now = monad.eval(System.currentTimeMillis())

      convertToFuture.toFuture(
        now
          .flatMap { start =>
            timeoutToNone(req.send(), 100)
              .map { r =>
                (System.currentTimeMillis() - start < 1000) shouldBe true
                r shouldBe None
              }
          }
      )
    }
  }
}
