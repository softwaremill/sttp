package sttp.client4.testing

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.SttpClientException.ReadException
import sttp.client4.{basicRequest, UriContext}
import sttp.monad.FutureMonad

import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BackendStubTests2 extends AnyFlatSpec with Matchers with ScalaFutures {
  it should "handle exceptions thrown instead of a response (asynchronous)" in {
    val backend: BackendStub[Future] = BackendStub.asynchronousFuture
      .whenRequestMatches(_ => true)
      .thenRespond(throw new TimeoutException())

    val result = basicRequest.get(uri"http://example.org").send(backend)
    result.failed.map(_ shouldBe a[ReadException]).futureValue
  }
}
