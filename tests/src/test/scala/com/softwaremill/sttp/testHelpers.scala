package com.softwaremill.sttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future
import scala.language.higherKinds

trait TestHttpServer extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>
  protected implicit val actorSystem: ActorSystem = ActorSystem("sttp-test")
  import actorSystem.dispatcher

  protected implicit val materializer = ActorMaterializer()
  protected val endpoint = uri"http://localhost:$port"

  override protected def beforeAll(): Unit = {
    Http().bindAndHandle(serverRoutes, "localhost", port).futureValue
  }

  override protected def afterAll(): Unit = {
    actorSystem.terminate().futureValue
  }

  def serverRoutes: Route
  def port: Int
}

trait ForceWrapped extends ScalaFutures { this: Suite =>
  trait ForceWrappedValue[R[_]] {
    def force[T](wrapped: R[T]): T
  }
  object ForceWrappedValue {
    val id = new ForceWrappedValue[Id] {
      override def force[T](wrapped: Id[T]): T =
        wrapped
    }
    val future = new ForceWrappedValue[Future] {
      override def force[T](wrapped: Future[T]): T =
        wrapped.futureValue
    }
  }
  implicit class ForceDecorator[R[_], T](wrapped: R[T]) {
    def force()(implicit fwv: ForceWrappedValue[R]): T = fwv.force(wrapped)
  }
}
