package com.softwaremill.sttp

import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.akkahttp.AkkaHttpSttpHandler
import com.softwaremill.sttp.asynchttpclient.monix.MonixAsyncHttpClientHandler
import com.typesafe.scalalogging.StrictLogging
import monix.reactive.Observable
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

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
  monixStreamingTests()

  val body = "streaming test"

  def akkaStreamingTests(): Unit = {
    implicit val handler = AkkaHttpSttpHandler.usingActorSystem(actorSystem)

    "Akka HTTP" should "stream request body" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .streamBody(Source.single(ByteString(body)))
        .send()
        .futureValue

      response.body should be(body)
    }

    it should "receive a stream" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .body(body)
        .response(asStream[Source[ByteString, Any]])
        .send()
        .futureValue

      val responseBody = response.body.runReduce(_ ++ _).futureValue.utf8String

      responseBody should be(body)
    }
  }

  def monixStreamingTests(): Unit = {
    implicit val handler = MonixAsyncHttpClientHandler()
    import monix.execution.Scheduler.Implicits.global

    val body = "streaming test"

    "Monix Async Http Client" should "stream request body" in {
      val source = Observable.fromIterable(
        body.getBytes("utf-8").map(b => ByteBuffer.wrap(Array(b))))

      val response = sttp
        .post(uri"$endpoint/echo")
        .streamBody(source)
        .send()
        .runAsync
        .futureValue

      response.body should be(body)
    }

    it should "receive a stream" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .body(body)
        .response(asStream[Observable[ByteBuffer]])
        .send()
        .runAsync
        .futureValue

      val bytes = response.body
        .flatMap(bb => Observable.fromIterable(bb.array()))
        .toListL
        .runAsync
        .futureValue
        .toArray

      new String(bytes, "utf-8") should be(body)
    }
  }
}
