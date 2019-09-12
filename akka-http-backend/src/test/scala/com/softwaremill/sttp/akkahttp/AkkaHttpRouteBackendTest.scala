package com.softwaremill.sttp.akkahttp

import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import com.softwaremill.sttp.SttpBackend
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.stream.ActorMaterializer
import com.github.ghik.silencer.silent

class AkkaHttpRouteBackendTest extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  @silent("discarded")
  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  val backend: SttpBackend[Future, Nothing] = {
    AkkaHttpBackend.usingClient(system, http = AkkaHttpClient.stubFromRoute(Routes.route))
  }

  import com.softwaremill.sttp._

  "matched route" should {

    "respond" in {
      backend.send(sttp.get(uri"localhost/hello")).map { response =>
        response.code shouldBe 200
        response.body.right.get shouldBe "Hello, world!"
      }
    }
  }

  "unmatched route" should {
    "respond with 404" in {
      backend.send(sttp.get(uri"http://localhost/not-matching")).map { response =>
        response.code shouldBe 404
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
