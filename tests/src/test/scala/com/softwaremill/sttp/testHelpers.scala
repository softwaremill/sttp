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
    val scalazTask = new ForceWrappedValue[scalaz.concurrent.Task] {
      override def force[T](wrapped: scalaz.concurrent.Task[T]): T =
        wrapped.unsafePerformSync
    }
    val monixTask = new ForceWrappedValue[monix.eval.Task] {
      import monix.execution.Scheduler.Implicits.global

      override def force[T](wrapped: monix.eval.Task[T]): T =
        wrapped.runAsync.futureValue
    }
  }
  implicit class ForceDecorator[R[_], T](wrapped: R[T]) {
    def force()(implicit fwv: ForceWrappedValue[R]): T = fwv.force(wrapped)
  }
}

object EvalScala {
  import scala.tools.reflect.ToolBox

  def apply(code: String): Any = {
    val m = scala.reflect.runtime.currentMirror
    val tb = m.mkToolBox()
    tb.eval(tb.parse(code))
  }
}
