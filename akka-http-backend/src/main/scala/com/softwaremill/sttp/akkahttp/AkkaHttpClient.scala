package com.softwaremill.sttp.akkahttp

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.{ConnectionPoolSettings, ParserSettings, RoutingSettings}
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.stream.Materializer
import akka.http.scaladsl.server.RoutingLog
import scala.concurrent.ExecutionContextExecutor
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.ExceptionHandler

trait AkkaHttpClient {
  def singleRequest(
      request: HttpRequest,
      settings: ConnectionPoolSettings
  ): Future[HttpResponse]
}

object AkkaHttpClient {
  def fromAkkaHttpExt(
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
