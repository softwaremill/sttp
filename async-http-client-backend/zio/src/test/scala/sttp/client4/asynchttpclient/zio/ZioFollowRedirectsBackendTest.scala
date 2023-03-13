package sttp.client4.asynchttpclient.zio

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Effect
import sttp.client4._
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioTestBase}
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.model.{Header, StatusCode}
import sttp.monad.MonadError
import zio.{Task, ZIO}

class ZioFollowRedirectsBackendTest extends AsyncFlatSpec with Matchers with ZioTestBase {
  it should "properly handle invalid redirect URIs" in {
    val stubBackend: Backend[Task] = new Backend[Task] {
      override def send[T](request: GenericRequest[T, Any with Effect[Task]]): Task[Response[T]] = {
        ZIO.succeed(
          if (request.uri.toString.contains("redirect"))
            Response.ok("ok".asInstanceOf[T])
          else
            Response.apply(
              "".asInstanceOf[T],
              StatusCode.PermanentRedirect,
              "",
              List(Header.location("i nvalid redirect"))
            )
        )
      }

      override def close(): Task[Unit] = ZIO.unit
      override def monad: MonadError[Task] = new RIOMonadAsyncError[Any]
    }

    val result: Task[Response[_]] = basicRequest
      .response(asStringAlways)
      .get(uri"http://localhost")
      .send(FollowRedirectsBackend(stubBackend))

    convertZioTaskToFuture.toFuture(result).map { r =>
      r.body shouldBe "ok"
    }
  }
}
