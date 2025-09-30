package sttp.client4.impl.cats

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.listener.ListenerBackend
import sttp.client4.listener.RequestListener
import sttp.model.ResponseMetadata

class CatsListenerBackendTest extends AnyFlatSpec with Matchers {
  // #2669
  it should "notify the listener when a request is interrupted" in {
    for {
      inResponse <- Semaphore[IO](0)
      trail <- Ref.of[IO, List[String]](Nil)
      baseBackend = HttpClientCatsBackend.stub[IO].whenAnyRequest.thenRespondF {
        inResponse.release *> IO.never
      }
      backend = ListenerBackend(baseBackend, spyingListener(trail))
      // send the request in the background ...
      sendingFiber <- basicRequest.get(uri"http://example.org").send(backend).void.start
      // wait until the request is being processed ...
      _ <- inResponse.acquire
      // the counter should be 1, as it should be incremented in the listener's "before"
      trailBefore <- trail.get
      _ = trailBefore shouldBe List("before")
      // cancel the request, interrupting the IO.never ...
      _ <- sendingFiber.cancel
      // and now the counter should be decreased again
      trailAfter <- trail.get
      _ = trailAfter shouldBe List("before", "exception")
    } yield ()
  }.unsafeRunSync()

  it should "properly notify listener in case of normal completion" in {
    for {
      inResponse <- Semaphore[IO](0)
      trail <- Ref.of[IO, List[String]](Nil)
      baseBackend = HttpClientCatsBackend.stub[IO].whenAnyRequest.thenRespondOk()
      backend = ListenerBackend(baseBackend, spyingListener(trail))
      _ <- basicRequest.get(uri"http://example.org").send(backend)
      trailAfter <- trail.get
      _ = trailAfter shouldBe List("before", "response handled")
    } yield ()
  }.unsafeRunSync()

  it should "properly notify listener in case of an exception" in {
    for {
      inResponse <- Semaphore[IO](0)
      trail <- Ref.of[IO, List[String]](Nil)
      baseBackend = HttpClientCatsBackend.stub[IO].whenAnyRequest.thenThrow(new RuntimeException("test exception"))
      backend = ListenerBackend(baseBackend, spyingListener(trail))
      _ <- basicRequest.get(uri"http://example.org").send(backend).handleErrorWith { case _ => IO.unit }
      trailAfter <- trail.get
      _ = trailAfter shouldBe List("before", "exception")
    } yield ()
  }.unsafeRunSync()

  def spyingListener(trail: Ref[IO, List[String]]) = new RequestListener[IO, Unit] {
    override def before(request: GenericRequest[_, _]): IO[Unit] = trail.update(_ :+ "before")
    override def responseBodyReceived(request: GenericRequest[_, _], response: ResponseMetadata, tag: Unit): Unit = ()
    override def responseHandled(
        request: GenericRequest[_, _],
        response: ResponseMetadata,
        tag: Unit,
        exception: Option[ResponseException[_]]
    ): IO[Unit] = trail.update(_ :+ "response handled")
    override def exception(
        request: GenericRequest[_, _],
        tag: Unit,
        exception: Throwable,
        responseBodyReceivedCalled: Boolean
    ): IO[Unit] = trail.update(_ :+ s"exception")
  }
}
