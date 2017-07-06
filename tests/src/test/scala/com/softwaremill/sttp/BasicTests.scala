package com.softwaremill.sttp

import java.net.URI

import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.softwaremill.sttp.akkahttp.AkkaHttpSttpHandler
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.Future
import scala.language.higherKinds

class BasicTests extends FlatSpec with Matchers with BeforeAndAfterAll with ScalaFutures with StrictLogging with IntegrationPatience {
  private def paramsToString(m: Map[String, String]): String = m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private val serverRoutes =
    path("echo") {
      get {
        parameterMap { params =>
          complete(s"GET /echo ${paramsToString(params)}")
        }
      } ~
      post {
        parameterMap { params =>
          entity(as[String]) { body: String =>
            complete(s"POST /echo ${paramsToString(params)} $body")
          }
        }
      }
    }

  private implicit val actorSystem: ActorSystem = ActorSystem("sttp-test")
  import actorSystem.dispatcher

  private implicit val materializer = ActorMaterializer()
  private val endpoint = "http://localhost:51823"

  override protected def beforeAll(): Unit = {
    Http().bindAndHandle(serverRoutes, "localhost", 51823)
  }

  override protected def afterAll(): Unit = {
    actorSystem.terminate().futureValue
  }

  trait ForceWrappedValue[R[_]] {
    def force[T](wrapped: R[T]): T
  }

  runTests("HttpURLConnection", HttpConnectionSttpHandler, new ForceWrappedValue[Id] {
    override def force[T](wrapped: Id[T]): T = wrapped
  })
  runTests("Akka HTTP", new AkkaHttpSttpHandler(actorSystem), new ForceWrappedValue[Future] {
    override def force[T](wrapped: Future[T]): T = wrapped.futureValue
  })

  def runTests[R[_]](name: String, handler: SttpHandler[R, Nothing], forceResponse: ForceWrappedValue[R]): Unit = {
    implicit val h = handler

    name should "make a get request with parameters" in {
      val response = sttp
        .get(new URI(endpoint + "/echo?p2=v2&p1=v1"))
        .send(responseAsString)

      val fc = forceResponse.force(response).body
      fc should be ("GET /echo p1=v1 p2=v2")
    }
  }
}
