package sttp.client4.logging

import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.model.ResponseMetadata

import scala.concurrent.duration.Duration
import sttp.client4.ResponseException

/** Performs logging before requests are sent and after requests complete successfully or with an exception. */
trait Log[F[_]] {
  def beforeRequestSend(request: GenericRequest[_, _]): F[Unit]

  /** @param exception
    *   A [[ResponseException]] that might occur when handling the response (e.g. parsing).
    */
  def response(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      responseBody: Option[String],
      timings: Option[ResponseTimings],
      exception: Option[ResponseException[_]]
  ): F[Unit]

  def requestException(request: GenericRequest[_, _], timing: Option[Duration], exception: Throwable): F[Unit]
}

object Log {
  def default[F[_]](logger: Logger[F], config: LogConfig): Log[F] =
    new DefaultLog(
      logger,
      config,
      LogContext.default(
        config.logRequestHeaders,
        config.logResponseHeaders,
        config.sensitiveHeaders,
        config.sensitiveQueryParams
      )
    )
}

/** Default implementation of [[Log]] to be used by the [[LoggingBackend]]. Creates default log messages and delegates
  * them to the given [[Logger]].
  */
class DefaultLog[F[_]](logger: Logger[F], config: LogConfig, logContext: LogContext) extends Log[F] {

  def beforeRequestSend(request: GenericRequest[_, _]): F[Unit] = {
    val _logRequestBody = request.loggingOptions.logRequestBody.getOrElse(config.logRequestBody)
    val _logRequestHeaders = request.loggingOptions.logRequestHeaders.getOrElse(config.logRequestHeaders)
    val _message =
      if (config.beforeCurlInsteadOfShow && _logRequestBody && _logRequestHeaders)
        request.toCurl(config.sensitiveHeaders, config.sensitiveQueryParams)
      else
        request.show(_logRequestBody, _logRequestHeaders, config.sensitiveHeaders, config.sensitiveQueryParams)
    logger(
      level = config.beforeRequestSendLogLevel,
      message = s"Sending request: ${_message}",
      exception = None,
      context = logContext.forRequest(request)
    )
  }

  override def response(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      responseBody: Option[String],
      timings: Option[ResponseTimings],
      exception: Option[ResponseException[_]]
  ): F[Unit] = {
    val responseWithBody = Response(
      responseBody.getOrElse(""),
      response.code,
      response.statusText,
      response.headers,
      Nil,
      request
    )

    logger(
      level = config.responseLogLevel(response.code),
      message = {
        val responseAsString = responseWithBody.show(
          responseBody.isDefined && request.loggingOptions.logResponseBody.getOrElse(config.logResponseBody),
          request.loggingOptions.logResponseHeaders.getOrElse(config.logResponseHeaders),
          config.sensitiveHeaders
        )
        s"Request: ${request.showBasicSafe(config.sensitiveQueryParams)}${took(timings)}, response: $responseAsString"
      },
      exception = exception,
      context = logContext.forResponse(request, response, timings)
    )
  }

  override def requestException(
      request: GenericRequest[_, _],
      timing: Option[Duration],
      exception: Throwable
  ): F[Unit] =
    logger(
      level = config.responseExceptionLogLevel,
      message = s"Exception when sending request: ${request.showBasic}${tookFromDuration(timing)}",
      exception = Some(exception),
      context = logContext.forRequest(request)
    )

  private def elapsed(d: Duration): String = f"${d.toMillis / 1000.0}%.3fs"
  private def tookFromDuration(timing: Option[Duration]): String = timing.fold("")(t => f", took: ${elapsed(t)}")
  private def took(timings: Option[ResponseTimings]): String = {
    timings.fold("") { t =>
      t.bodyReceived match {
        case None     => s", took: ${elapsed(t.bodyHandled)}"
        case Some(br) => s", took: (body=${elapsed(br)}, full=${elapsed(t.bodyHandled)})"
      }
    }
  }
}
