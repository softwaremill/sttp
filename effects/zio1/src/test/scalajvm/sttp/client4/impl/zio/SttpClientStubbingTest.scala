package sttp.client4.impl.zio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.monad.MonadError
import zio.{RIO, Ref, Tag}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object TestStreamClientStubbing extends StreamClientStubbing[Any, Any] {
  override private[sttp] def serviceTag: Tag[TestStreamClientStubbing.Service] = implicitly
  override private[sttp] def sttpBackendTag: Tag[TestStreamClientStubbing.Backend] = implicitly
}

object TestWebSocketStreamClientStubbing extends WebSocketStreamClientStubbing[Any, Any] {
  override private[sttp] def serviceTag: Tag[TestWebSocketStreamClientStubbing.Service] = implicitly
  override private[sttp] def sttpBackendTag: Tag[TestWebSocketStreamClientStubbing.Backend] = implicitly
}

class SttpClientStubbingTest extends AnyFlatSpec with Matchers with ZioTestBase {
  // a self-referencing `monad` compiles to an endless loop, so it's evaluated with a timeout
  private def monadOf(m: => MonadError[RIO[Any, *]]): MonadError[RIO[Any, *]] = Await.result(Future(m), 5.seconds)

  "StreamClientStubbing.proxy" should "use the outer monad" in {
    val ref = runtime.unsafeRun(Ref.make(TestStreamClientStubbing.backendStub))
    val m = monadOf(TestStreamClientStubbing.proxy(ref).monad)
    m should be theSameInstanceAs TestStreamClientStubbing.monad
    runtime.unsafeRun(m.map(m.unit(1))(_ + 1)) shouldBe 2
  }

  "WebSocketStreamClientStubbing.proxy" should "use the outer monad" in {
    val ref = runtime.unsafeRun(Ref.make(TestWebSocketStreamClientStubbing.backendStub))
    val m = monadOf(TestWebSocketStreamClientStubbing.proxy(ref).monad)
    m should be theSameInstanceAs TestWebSocketStreamClientStubbing.monad
    runtime.unsafeRun(m.map(m.unit(1))(_ + 1)) shouldBe 2
  }
}
