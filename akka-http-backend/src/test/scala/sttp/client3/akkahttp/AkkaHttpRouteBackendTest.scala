package sttp.client3.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import org.scalatest.BeforeAndAfterAll
import sttp.client3.SttpBackend
import sttp.model.StatusCode

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class AkkaHttpRouteBackendTest extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  val backend: SttpBackend[Future, Any] =
    AkkaHttpBackend.usingClient(system, http = AkkaHttpClient.stubFromRoute(Routes.route))

  import sttp.client3._

  "matched route" should {

    "respond" in {
      backend.send(basicRequest.get(uri"http://localhost/hello")).map { response =>
        response.code shouldBe StatusCode.Ok
        response.body.right.get shouldBe "Hello, world!"
      }
    }
  }

  "unmatched route" should {
    "respond with 404" in {
      backend.send(basicRequest.get(uri"http://localhost/not-matching")).map { response =>
        response.code shouldBe StatusCode.NotFound
        response.body.left.get shouldBe "The requested resource could not be found."
      }
    }
  }

}

object Routes {
  import akka.http.scaladsl.server.Directives._

  val route: Route =
    pathPrefix("hello") {
      complete("Hello, world!")
    }
}
