package sttp.client4.pekkohttp

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, RoutingLog}
import org.apache.pekko.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Source}

import scala.concurrent.{ExecutionContextExecutor, Future}

private[pekkohttp] object PekkoWatchTerminationCompat {
  implicit final class FlowWatchTerminationCompat[In, Out, Mat](private val flow: Flow[In, Out, Mat]) {
    def watchTerminationCompat[Mat2](matF: (Mat, Future[Done]) => Mat2): Flow[In, Out, Mat2] =
      flow.watchTermination(matF)
  }

  implicit final class SourceWatchTerminationCompat[Out, Mat](private val source: Source[Out, Mat]) {
    def watchTerminationCompat[Mat2](matF: (Mat, Future[Done]) => Mat2): Source[Out, Mat2] =
      source.watchTermination(matF)
  }
}

private[pekkohttp] object PekkoRouteCompat {
  def asyncHandler(route: Route)(implicit
      routingSettings: RoutingSettings,
      parserSettings: ParserSettings,
      materializer: Materializer,
      routingLog: RoutingLog,
      executionContext: ExecutionContextExecutor,
      rejectionHandler: RejectionHandler,
      exceptionHandler: ExceptionHandler
  ): HttpRequest => Future[HttpResponse] = {
    val sealedRoute = Route.seal(route)(routingSettings, rejectionHandler, exceptionHandler)
    // Route.toFunction creates its own routing/parser settings; keep the custom handlers
    // by sealing the route first. The remaining settings are not part of its public API.
    val _ = (parserSettings, routingLog, executionContext)
    Route.toFunction(sealedRoute)(materializer.system)
  }
}
