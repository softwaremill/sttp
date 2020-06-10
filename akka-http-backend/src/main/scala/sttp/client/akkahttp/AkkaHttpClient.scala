package sttp.client.akkahttp

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, RoutingLog}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings, ParserSettings, RoutingSettings}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait AkkaHttpClient {
  def singleRequest(
      request: HttpRequest,
      settings: ConnectionPoolSettings
  ): Future[HttpResponse]

  def singleWebsocketRequest[WS_RESULT](
      request: WebSocketRequest,
      clientFlow: Flow[Message, Message, WS_RESULT],
      settings: ClientConnectionSettings
  )(implicit ec: ExecutionContext, mat: Materializer): Future[(WebSocketUpgradeResponse, WS_RESULT)]
}

object AkkaHttpClient {
  def default(
      system: ActorSystem,
      connectionContext: Option[HttpsConnectionContext],
      customLog: Option[LoggingAdapter]
  ): AkkaHttpClient =
    new AkkaHttpClient {
      private val http = Http()(system)

      override def singleRequest(
          request: HttpRequest,
          settings: ConnectionPoolSettings
      ): Future[HttpResponse] = {
        http.singleRequest(
          request,
          connectionContext.getOrElse(http.defaultClientHttpsContext),
          settings,
          customLog.getOrElse(system.log)
        )
      }

      override def singleWebsocketRequest[WS_RESULT](
          request: WebSocketRequest,
          clientFlow: Flow[Message, Message, WS_RESULT],
          settings: ClientConnectionSettings
      )(implicit ec: ExecutionContext, mat: Materializer): Future[(WebSocketUpgradeResponse, WS_RESULT)] = {
        val (wsResponse, wsResult) = http.singleWebSocketRequest(
          request,
          clientFlow,
          connectionContext.getOrElse(http.defaultClientHttpsContext),
          None,
          settings,
          customLog.getOrElse(system.log)
        )
        wsResponse.map((_, wsResult))
      }
    }

  def stubFromAsyncHandler(run: HttpRequest => Future[HttpResponse]): AkkaHttpClient =
    new AkkaHttpClient {
      def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse] =
        run(request)

      override def singleWebsocketRequest[WS_RESULT](
          request: WebSocketRequest,
          clientFlow: Flow[Message, Message, WS_RESULT],
          settings: ClientConnectionSettings
      )(implicit ec: ExecutionContext, mat: Materializer): Future[(WebSocketUpgradeResponse, WS_RESULT)] =
        Future.failed(new RuntimeException("Websockets are not supported"))
    }

  def stubFromRoute(route: Route)(implicit
      routingSettings: RoutingSettings,
      parserSettings: ParserSettings,
      materializer: Materializer,
      routingLog: RoutingLog,
      executionContext: ExecutionContextExecutor = null,
      rejectionHandler: RejectionHandler = RejectionHandler.default,
      exceptionHandler: ExceptionHandler = null
  ): AkkaHttpClient = stubFromAsyncHandler(Route.asyncHandler(route))
}
