package sttp.client3.akkahttp.zio

import _root_.zio._
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.client3.akkahttp.AkkaHttpBackend.EncodingHandler
import sttp.client3.akkahttp.{AkkaHttpBackend, AkkaHttpClient}
//import sttp.client3.httpclient.zio.{SttpClient, SttpClientStubbing}
//import sttp.client3.httpclient.zio.SttpClientStubbing.SttpClientStubbing
import sttp.client3.testing.SttpBackendStub
import sttp.client3._
import sttp.model.HeaderNames
import sttp.monad.MonadError

import scala.concurrent.Future
//import io.netty.buffer.{ByteBuf, Unpooled}
//import org.asynchttpclient._
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
//import sttp.client3.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client3.impl.zio.RIOMonadAsyncError
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

class AkkaHttpClientZioBackend (
          underlying: SttpBackend[Future, AkkaStreams with WebSockets]
   ) extends SttpBackend[Task, ZioStreams with WebSockets] {

  override def send[T, R >: Any with capabilities.Effect[Task]](request: Request[T, R]): Task[Response[T]] = {
    ZIO.fromFuture(ec =>

//      underlying.send(request.asInstanceOf[Request[T, ZioStreams with WebSockets with capabilities.Effect[Future]]])).map(r => r.asInstanceOf[Response[T]])
      underlying.send(request.asInstanceOf[Request[T, Any with capabilities.Effect[Future]]])).map(r => r.asInstanceOf[Response[T]])
  }

  override def close(): Task[Unit] = ZIO.fromFuture(ec => underlying.close())

  override def responseMonad: MonadError[Task] = new RIOMonadAsyncError
}

object AkkaHttpClientZioBackend {

  def apply(
             underlying: SttpBackend[Future, AkkaStreams with WebSockets]
           ): Task[SttpBackend[Task, ZioStreams with WebSockets]] = ZIO
    .runtime[Any]
    .flatMap(r =>
      Task.effect(
        applyR(
          r,
          underlying
        )
      )
    )

  private def applyR[R](
                         runtime: Runtime[R],
                         underlying: SttpBackend[Future, AkkaStreams with WebSockets],
                       ): SttpBackend[Task, ZioStreams with WebSockets] =
    new FollowRedirectsBackend(
      new AkkaHttpClientZioBackend(underlying)
    )


//  def stub: SttpBackendStub[Task, ZioStreams with WebSockets] =
  def stub: SttpBackendStub[Task, Any] =
    SttpBackendStub(new RIOMonadAsyncError[Any])

  val stubLayer: Layer[Throwable, SttpClient with SttpClientStubbing] =
    SttpClientStubbing.layer
}
