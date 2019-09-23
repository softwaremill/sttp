package sttp.client.akkahttp

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, RoutingLog}
import akka.http.scaladsl.settings.{ConnectionPoolSettings, ParserSettings, RoutingSettings}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer

import scala.concurrent.{ExecutionContextExecutor, Future}

trait AkkaHttpClient {
  def singleRequest(
      request: HttpRequest,
      settings: ConnectionPoolSettings
  ): Future[HttpResponse]
}

object AkkaHttpClient {
  def default(
      system: ActorSystem,
      connectionContext: Option[HttpsConnectionContext],
      customLog: Option[LoggingAdapter]
  ): AkkaHttpClient = new AkkaHttpClient {

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
  }

  def stubFromAsyncHandler(run: HttpRequest => Future[HttpResponse]): AkkaHttpClient = new AkkaHttpClient {
    def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse] =
      run(request)
  }

  def stubFromRoute(route: Route)(
      implicit routingSettings: RoutingSettings,
      parserSettings: ParserSettings,
      materializer: Materializer,
      routingLog: RoutingLog,
      executionContext: ExecutionContextExecutor = null,
      rejectionHandler: RejectionHandler = RejectionHandler.default,
      exceptionHandler: ExceptionHandler = null
  ): AkkaHttpClient = stubFromAsyncHandler(Route.asyncHandler(route))
}
