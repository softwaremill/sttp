package com.softwaremill.sttp.akkahttp

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import com.softwaremill.sttp.SttpBackend
import org.scalatest.{AsyncWordSpec, Matchers}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import akka.http.scaladsl.testkit.RouteTestTimeout

class AkkaHttpRouteBackendTest extends AsyncWordSpec with ScalatestRouteTest with Matchers {

  implicit val timeout = RouteTestTimeout(5.seconds)

  lazy val backend: SttpBackend[Future, Nothing] = {
    AkkaHttpBackend.usingActorSystem(system) {
      new AkkaHttpClient {
        override def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse] = {
          val p = Promise[HttpResponse]()
          val f = p.future

          request ~> Route.seal(Routes.route) ~> check {
            p.success(response)
          }

          f
        }
      }
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

  "future route" should {
    "respond with 200" in {
      backend.send(sttp.get(uri"http://localhost/futures/quick")).map { response =>
        response.code shouldBe 200
        response.body.right.get shouldBe "done-quick"
      }
    }

    "respond with 200 in the buggy case" in {
      backend.send(sttp.get(uri"http://localhost/futures/buggy")).map { response =>
        response.code shouldBe 200
        response.body.right.get shouldBe "done-buggy"
      }
    }

    "respond with 200 after a long running future" in {
      backend.send(sttp.get(uri"http://localhost/futures/long")).map { response =>
        response.code shouldBe 200
        response.body.right.get shouldBe "done-long"
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

  def route(implicit ec: ExecutionContext, system: ActorSystem): Route =
    pathPrefix("hello") {
      complete("Hello, world!")
    } ~ pathPrefix("futures") {
      pathPrefix("quick") {
        complete(Future.successful("done-quick"))
      } ~ pathPrefix("buggy") {
        complete(Future.successful(()).map(_ => "done-buggy"))
      } ~ pathPrefix("long") {
        complete(longFuture())
      }
    }

  private def longFuture()(implicit ec: ExecutionContext, system: ActorSystem): Future[String] = {
    val promise = Promise[String]()

    system.scheduler.scheduleOnce(1.seconds) {
      val _ = promise.success("done-long")
    }

    promise.future
  }
}
