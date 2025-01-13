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
      throwable = None,
      context = logContext.forRequest(request)
    )

  override def response(
      request: GenericRequest[_, _],
      response: Response[_],
      responseBody: Option[String],
      elapsed: Option[Duration]
  ): F[Unit] = handleResponse(request, response, responseBody, elapsed, None)

  private def handleResponse(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      responseBody: Option[String],
      elapsed: Option[Duration],
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
        s"Request: ${request.showBasic}${took(elapsed)}, response: $responseAsString"
      },
      throwable = e,
      context = logContext.forResponse(request, response, elapsed)
    )
  }

  override def requestException(request: GenericRequest[_, _], elapsed: Option[Duration], e: Exception): F[Unit] =
    ResponseException.find(e) match {
      case Some(re) =>
        handleResponse(request, re.response, None, elapsed, Some(e))
      case None =>
        logger(
          level = responseExceptionLogLevel,
          message = s"Exception when sending request: ${request.showBasic}${took(elapsed)}",
          throwable = Some(e),
          context = logContext.forRequest(request)
        )
    }

  private def took(elapsed: Option[Duration]): String = elapsed.fold("")(e => f", took: ${e.toMillis / 1000.0}%.3fs")
}
