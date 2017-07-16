package com.softwaremill.sttp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.akkahttp.AkkaHttpSttpHandler
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import com.softwaremill.sttp.akkahttp._

class StreamingTests
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with StrictLogging
    with IntegrationPatience
    with TestHttpServer {

  override val serverRoutes: Route =
    path("echo") {
      post {
        parameterMap { params =>
          entity(as[String]) { body: String =>
            complete(body)
          }
        }
      }
    }

  override def port = 51824

  akkaStreamingTests()

  def akkaStreamingTests(): Unit = {
    implicit val handler = new AkkaHttpSttpHandler(actorSystem)

    val body = "streaming test"

    "Akka HTTP" should "stream request body" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .body(Source.single(ByteString(body)))
        .send(responseAsString)
        .futureValue

      response.body should be(body)
    }

    it should "receive a stream" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .body(body)
        .send(responseAsStream[Source[ByteString, Any]])
        .futureValue

      val responseBody = response.body.runReduce(_ ++ _).futureValue.utf8String

      responseBody should be(body)
    }
  }
}
