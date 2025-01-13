package sttp.client4.logging

import sttp.client4.{GenericRequest, HttpError, Response}
import sttp.model.StatusCode

import scala.concurrent.duration.Duration

/** Performs logging before requests are sent and after requests complete successfully or with an exception. */
trait Log[F[_]] {
  def beforeRequestSend(request: GenericRequest[_, _]): F[Unit]
  def response(
      request: GenericRequest[_, _],
      response: Response[_],
      responseBody: Option[String],
      elapsed: Option[Duration]
  ): F[Unit]
  def requestException(
      request: GenericRequest[_, _],
      elapsed: Option[Duration],
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
      message = s"Sending request: ${
          if (beforeCurlInsteadOfShow && _logRequestBody && _logRequestHeaders) request.toCurl(sensitiveHeaders)
          else request.show(includeBody = _logRequestBody, _logRequestHeaders, sensitiveHeaders)
        }",
      context = logContext.forRequest(request)
    )

  override def response(
      request: GenericRequest[_, _],
      response: Response[_],
      responseBody: Option[String],
      elapsed: Option[Duration]
  ): F[Unit] = handleResponse(
    request.showBasic,
    response,
    responseBody,
    request.loggingOptions.logResponseBody.getOrElse(responseBody.isDefined),
    request.loggingOptions.logResponseHeaders.getOrElse(logResponseHeaders),
    elapsed
  )

  private def handleResponse(
      showBasic: String,
      response: Response[_],
      responseBody: Option[String],
      logResponseBody: Boolean,
      _logResponseHeaders: Boolean,
      elapsed: Option[Duration]
  ): F[Unit] =
    logger(
      level = responseLogLevel(response.code),
      message = {
        val responseAsString =
          response
            .copy(body = responseBody.getOrElse(""))
            .show(logResponseBody, _logResponseHeaders, sensitiveHeaders)
        s"Request: $showBasic${took(elapsed)}, response: $responseAsString"
      },
      context = logContext.forResponse(response, elapsed)
    )

  override def requestException(request: GenericRequest[_, _], elapsed: Option[Duration], e: Exception): F[Unit] = {
    val logLevel = HttpError.find(e) match {
      case Some(HttpError(_, statusCode)) =>
        responseLogLevel(statusCode)
      case _ =>
        responseExceptionLogLevel
    }
    logger(
      level = logLevel,
      message = s"Exception when sending request: ${request.showBasic}${took(elapsed)}",
      throwable = e,
      context = logContext.forRequest(request)
    )
  }

  private def took(elapsed: Option[Duration]): String = elapsed.fold("")(e => f", took: ${e.toMillis / 1000.0}%.3fs")
}

object DefaultLog {
  def defaultResponseLogLevel(c: StatusCode): LogLevel =
    if (c.isClientError || c.isServerError) LogLevel.Warn else LogLevel.Debug
}
