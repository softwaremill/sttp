package sttp.client.ziotelemetry.opentracing

import io.opentracing.propagation.{Format, TextMapAdapter}

import scala.jdk.CollectionConverters._
import sttp.client._
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import zio._
import zio.telemetry.opentracing._

class ZioTelemetryOpenTracingBackend[-WS_HANLDER[_]](other: SttpBackend[Task, Nothing, WS_HANLDER], requestTagCollector: RequestTagCollector = RequestTagCollector.empty) extends SttpBackend[RIO[OpenTracing, *], Nothing, WS_HANLDER] {

  @SuppressWarnings(Array("scalafix:Disable.toString"))
  def send[T](request: Request[T, Nothing]): RIO[OpenTracing, Response[T]] = {
    val headers = scala.collection.mutable.Map.empty[String, String]
    val buffer  = new TextMapAdapter(headers.asJava)
    val tags = requestTagCollector.collect(request)
    OpenTracing.inject(Format.Builtin.HTTP_HEADERS, buffer).flatMap { _ =>
      val operation = other
        .send(request.headers(headers.toMap))
        .span(s"${request.method.method} ${request.uri.path.mkString("/")}")
        .tag("http.method", request.method.method)
        .tag("http.url", request.uri.toString())

      tags
        .foldLeft(operation) { case (op, (name, value)) => op.tag(name, value)}
        .flatMap(res => OpenTracing.tag("http.status_code", res.code.code) *> ZIO.succeed(res))
    }
  }

  def openWebsocket[T, WS_RESULT](request: Request[T, Nothing], handler: WS_HANLDER[WS_RESULT]): RIO[OpenTracing, WebSocketResponse[WS_RESULT]] =
    other.openWebsocket(request, handler)

  def close(): RIO[OpenTracing, Unit] = other.close()

  def responseMonad: MonadError[RIO[OpenTracing, *]] = new RIOMonadAsyncError[OpenTracing]
}

trait RequestTagCollector {
  def collect[T](request: Request[T, Nothing]): Map[String, String]
}

object RequestTagCollector {
  val empty: RequestTagCollector = new RequestTagCollector {
    def collect[T](request: Request[T, Nothing]): Map[String, String] = Map.empty
  }
}