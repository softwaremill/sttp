package com.softwaremill.sttp.akkahttp

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.sttp.SttpBackend
import org.scalatest.{AsyncWordSpec, Matchers}

import scala.concurrent.Future

class AkkaHttpRouteBackendTest extends AsyncWordSpec with ScalatestRouteTest with Matchers {

  lazy val backend: SttpBackend[Future, Nothing] = {
    AkkaHttpBackend.usingActorSystem(system) {
      AkkaHttpClient.fromStrict(request => (request ~> Route.seal(Routes.route)).response)
    }
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
      backend.send(sttp.get(uri"localhost/not-matching")).map { response =>
        response.code shouldBe 404
        response.body.left.get shouldBe "The requested resource could not be found."
      }
    }
  }

}

object Routes {
  import akka.http.scaladsl.server.Directives._

  val route: Route = pathPrefix("hello") {
    complete("Hello, world!")
  }
}
