package sttp.client3.pekkohttp

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.LoggingAdapter
import pekko.http.scaladsl.model.ws.{Message, WebSocketRequest, WebSocketUpgradeResponse}
import pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import pekko.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, RoutingLog}
import pekko.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings, ParserSettings, RoutingSettings}
import pekko.http.scaladsl.{Http, HttpsConnectionContext}
import pekko.stream.Materializer
import pekko.stream.scaladsl.Flow

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait PekkoHttpClient {
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

object PekkoHttpClient {
  def default(
      system: ActorSystem,
      connectionContext: Option[HttpsConnectionContext],
      customLog: Option[LoggingAdapter]
  ): PekkoHttpClient =
    new PekkoHttpClient {
      private val http = Http()(system)

      override def singleRequest(
          request: HttpRequest,
          settings: ConnectionPoolSettings
      ): Future[HttpResponse] =
        http.singleRequest(
          request,
          connectionContext.getOrElse(http.defaultClientHttpsContext),
          settings,
          customLog.getOrElse(system.log)
        )

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

  def stubFromAsyncHandler(run: HttpRequest => Future[HttpResponse]): PekkoHttpClient =
    new PekkoHttpClient {
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
  ): PekkoHttpClient = stubFromAsyncHandler(Route.asyncHandler(route))
}
