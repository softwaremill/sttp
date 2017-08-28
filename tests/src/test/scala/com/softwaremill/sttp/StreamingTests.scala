package com.softwaremill.sttp

import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.akkahttp.AkkaHttpSttpHandler
import com.softwaremill.sttp.asynchttpclient.monix.MonixAsyncHttpClientHandler
import com.softwaremill.sttp.okhttp.monix.OkHttpMonixClientHandler
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.language.higherKinds

class StreamingTests
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with StrictLogging
    with IntegrationPatience
    with ForceWrapped
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

  type BodyProducer[S] = String => S
  type BodyConsumer[S] = S => String

  override def port = 51824
  val body = "streaming test"

  val akkaHandler = AkkaHttpSttpHandler.usingActorSystem(actorSystem)
  val monixAsyncHttpClient = MonixAsyncHttpClientHandler()
  val monixOkHttpClient = OkHttpMonixClientHandler()

  val akkaHttpBodyProducer: BodyProducer[Source[ByteString, Any]] = s =>
    Source.single(ByteString(s))
  val akkaHttpBodyConsumer: BodyConsumer[Source[ByteString, Any]] =
    _.runReduce(_ ++ _).futureValue.utf8String

  val monixBodyProducer: BodyProducer[Observable[ByteBuffer]] =
    s =>
      Observable.fromIterable(
        s.getBytes("utf-8").map(b => ByteBuffer.wrap(Array(b))))

  val monixBodyConsumer: BodyConsumer[Observable[ByteBuffer]] = stream =>
    new String(stream
                 .flatMap(bb => Observable.fromIterable(bb.array()))
                 .toListL
                 .runAsync
                 .futureValue
                 .toArray,
               "utf-8")

  runTests("Akka HTTP", akkaHttpBodyProducer, akkaHttpBodyConsumer)(
    akkaHandler,
    ForceWrappedValue.future)
  runTests("Monix Async Http Client", monixBodyProducer, monixBodyConsumer)(
    monixAsyncHttpClient,
    ForceWrappedValue.monixTask)
  runTests("Monix OkHttp Client", monixBodyProducer, monixBodyConsumer)(
    monixOkHttpClient,
    ForceWrappedValue.monixTask)

  def runTests[R[_], S](name: String,
                        bodyProducer: BodyProducer[S],
                        bodyConsumer: BodyConsumer[S])(
      implicit handler: SttpHandler[R, S],
      forceResponse: ForceWrappedValue[R]): Unit = {
    name should "stream request body" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .streamBody(bodyProducer(body))
        .send()
        .force()

      response.body should be(body)
    }

    it should "receive a stream" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .body(body)
        .response(asStream[S])
        .send()
        .force()

      bodyConsumer(response.body) should be(body)
    }

    it should "receive a stream from an https site" in {
      val response = sttp
      // of course, you should never rely on the internet being available
      // in tests, but that's so much easier than setting up an https
      // testing server
        .get(uri"https://softwaremill.com")
        .response(asStream[S])
        .send()
        .force()

      bodyConsumer(response.body) should include("</div>")
    }
  }

  override protected def afterAll(): Unit = {
    akkaHandler.close()
    monixAsyncHttpClient.close()
    super.afterAll()
  }
}
