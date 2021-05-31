package sttp.client3.akkahttp

import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SttpBackendStubAkkaTests extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()

  override protected def afterAll(): Unit = {
    Await.result(system.terminate().map(_ => ()), 5.seconds)
  }

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    val backend = AkkaHttpBackend.stub
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")

    // when
    def r = basicRequest.get(uri"http://example.org/a/b/c").send(backend)

    val runningFutures = Seq.fill(4)(r)
    // then
    val result = Future
      .sequence(runningFutures)
      .futureValue
      .map(_.body)

    result should contain theSameElementsAs Seq("a", "b", "c", "a").map(Right(_))
  }
}
