package sttp.client4.logging

import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.model.ResponseMetadata
import sttp.model.StatusCode

import scala.concurrent.duration.Duration
import sttp.client4.ResponseException

/** Performs logging before requests are sent and after requests complete successfully or with an exception. */
trait Log[F[_]] {
  def beforeRequestSend(request: GenericRequest[_, _]): F[Unit]
  def response(
      request: GenericRequest[_, _],
      response: Response[_],
      responseBody: Option[String],
      timings: Option[ResponseTimings]
  ): F[Unit]
  def requestException(
      request: GenericRequest[_, _],
      timings: Option[ResponseTimings],
      e: Exception
  ): F[Unit]
}

object Log {
  def default[F[_]](
      logger: Logger[F],
      config: LogConfig
  ): Log[F] = new DefaultLog(
    logger,
    config.beforeCurlInsteadOfShow,
    config.logRequestBody,
    config.logRequestHeaders,
    config.logResponseHeaders,
    config.sensitiveHeaders,
    config.beforeRequestSendLogLevel,
    config.responseLogLevel,
    config.responseExceptionLogLevel,
    LogContext.default(config.logRequestHeaders, config.logResponseHeaders, config.sensitiveHeaders)
  )
}

/** Default implementation of [[Log]] to be used by the [[LoggingBackend]]. Creates default log messages and delegates
  * them to the given [[Logger]].
  */
class DefaultLog[F[_]](
    logger: Logger[F],
    beforeCurlInsteadOfShow: Boolean,
    logRequestBody: Boolean,
    logRequestHeaders: Boolean,
    logResponseHeaders: Boolean,
    sensitiveHeaders: Set[String],
    beforeRequestSendLogLevel: LogLevel,
    responseLogLevel: StatusCode => LogLevel,
    responseExceptionLogLevel: LogLevel,
    logContext: LogContext
) extends Log[F] {

  def beforeRequestSend(request: GenericRequest[_, _]): F[Unit] =
    before(
      request,
      request.loggingOptions.logRequestBody.getOrElse(logRequestBody),
      request.loggingOptions.logRequestHeaders.getOrElse(logRequestHeaders)
    )

  private def before(request: GenericRequest[_, _], _logRequestBody: Boolean, _logRequestHeaders: Boolean): F[Unit] =
    logger(
      level = beforeRequestSendLogLevel,
      message =
        s"Sending request: ${if (beforeCurlInsteadOfShow && _logRequestBody && _logRequestHeaders) request.toCurl(sensitiveHeaders)
          else request.show(includeBody = _logRequestBody, _logRequestHeaders, sensitiveHeaders)}",
      throwable = None,
      context = logContext.forRequest(request)
    )

  override def response(
      request: GenericRequest[_, _],
      response: Response[_],
      responseBody: Option[String],
      timings: Option[ResponseTimings]
  ): F[Unit] = handleResponse(request, response, responseBody, timings, None)

  private def handleResponse(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      responseBody: Option[String],
      timings: Option[ResponseTimings],
      e: Option[Throwable]
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
      level = responseLogLevel(response.code),
      message = {
        val responseAsString = responseWithBody.show(
          request.loggingOptions.logResponseBody.getOrElse(responseBody.isDefined),
          request.loggingOptions.logResponseHeaders.getOrElse(logResponseHeaders),
          sensitiveHeaders
        )
        s"Request: ${request.showBasic}${took(timings)}, response: $responseAsString"
      },
      throwable = e,
      context = logContext.forResponse(request, response, timings)
    )
  }

  override def requestException(
      request: GenericRequest[_, _],
      timings: Option[ResponseTimings],
      e: Exception
  ): F[Unit] =
    ResponseException.find(e) match {
      case Some(re) =>
        handleResponse(request, re.response, None, timings, Some(e))
      case None =>
        logger(
          level = responseExceptionLogLevel,
          message =
            s"Exception when sending request: ${request.showBasic}${tookFromDuration(timings.map(_.bodyProcessed))}",
          throwable = Some(e),
          context = logContext.forRequest(request)
        )
    }

  private def elapsed(d: Duration): String = f"${d.toMillis / 1000.0}%.3fs"
  private def tookFromDuration(timing: Option[Duration]): String = timing.fold("")(t => f", took: ${elapsed(t)}")
  private def took(timings: Option[ResponseTimings]): String =
    timings.fold("")(t => s", took: body=${elapsed(t.bodyReceived)}, full=${elapsed(t.bodyProcessed)}")
}
