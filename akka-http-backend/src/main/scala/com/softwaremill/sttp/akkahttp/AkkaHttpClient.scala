package com.softwaremill.sttp.akkahttp

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings

import scala.concurrent.Future

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

  def fromAsyncHandler(run: HttpRequest => Future[HttpResponse]): AkkaHttpClient = new AkkaHttpClient {
    def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse] =
      run(request)
  }
}
