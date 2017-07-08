package com.softwaremill.sttp

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.ByteBuffer

import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.softwaremill.sttp.akkahttp.AkkaHttpSttpHandler
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import better.files._

import scala.concurrent.Future
import scala.language.higherKinds

class BasicTests extends FlatSpec with Matchers with BeforeAndAfterAll with ScalaFutures with StrictLogging with IntegrationPatience {
  private def paramsToString(m: Map[String, String]): String = m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private val serverRoutes =
    path("echo") {
      get {
        parameterMap { params =>
          complete(List("GET", "/echo", paramsToString(params)).filter(_.nonEmpty).mkString(" "))
        }
      } ~
      post {
        parameterMap { params =>
          entity(as[String]) { body: String =>
            complete(List("POST", "/echo", paramsToString(params), body).filter(_.nonEmpty).mkString(" "))
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

    val postEcho = sttp.post(new URI(endpoint + "/echo"))
    val testBody = "this is the body"
    val testBodyBytes = testBody.getBytes("UTF-8")
    val expectedPostEchoResponse = "POST /echo this is the body"

    name should "post a string" in {
      val response = postEcho.data(testBody).send(responseAsString)
      val fc = forceResponse.force(response).body
      fc should be (expectedPostEchoResponse)
    }

    name should "post a byte array" in {
      val response = postEcho.data(testBodyBytes).send(responseAsString)
      val fc = forceResponse.force(response).body
      fc should be (expectedPostEchoResponse)
    }

    name should "post an input stream" in {
      val response = postEcho.data(new ByteArrayInputStream(testBodyBytes)).send(responseAsString)
      val fc = forceResponse.force(response).body
      fc should be (expectedPostEchoResponse)
    }

    name should "post a byte buffer" in {
      val response = postEcho.data(ByteBuffer.wrap(testBodyBytes)).send(responseAsString)
      val fc = forceResponse.force(response).body
      fc should be (expectedPostEchoResponse)
    }

    name should "post a file" in {
      val f = File.newTemporaryFile().write(testBody)
      try {
        val response = postEcho.data(f.toJava).send(responseAsString)
        val fc = forceResponse.force(response).body
        fc should be(expectedPostEchoResponse)
      } finally f.delete()
    }

    name should "post a path" in {
      val f = File.newTemporaryFile().write(testBody)
      try {
        val response = postEcho.data(f.toJava.toPath).send(responseAsString)
        val fc = forceResponse.force(response).body
        fc should be(expectedPostEchoResponse)
      } finally f.delete()
    }
  }
}
