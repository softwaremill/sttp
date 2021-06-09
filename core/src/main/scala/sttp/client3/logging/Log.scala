package sttp.client3.logging

import sttp.client3.{HttpError, Request, Response}
import sttp.model.{HeaderNames, StatusCode}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

/** Performs logging before requests are sent and after requests complete successfully or with an exception.
  */
trait Log[F[_]] {
  def beforeRequestSend(request: Request[_, _]): F[Unit]
  def response(
      request: Request[_, _],
      response: Response[_],
      responseBody: Option[String],
      elapsed: Option[Duration]
  ): F[Unit]
  def requestException(
      request: Request[_, _],
      elapsed: Option[Duration],
      e: Exception
  ): F[Unit]
}

/** Default implementation of [[Log]] to be used by the [[LoggingBackend]]. Creates default log messages and delegates
  * them to the given [[Logger]].
  */
class DefaultLog[F[_]](
    logger: Logger[F],
    beforeCurlInsteadOfShow: Boolean = false,
    logRequestBody: Boolean = false,
    logRequestHeaders: Boolean = true,
    logResponseHeaders: Boolean = true,
    sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
    beforeRequestSendLogLevel: LogLevel = LogLevel.Debug,
    responseLogLevel: StatusCode => LogLevel = DefaultLog.defaultResponseLogLevel,
    responseExceptionLogLevel: LogLevel = LogLevel.Error
) extends Log[F] {

  def beforeRequestSend(request: Request[_, _]): F[Unit] =
    logger(
      beforeRequestSendLogLevel, {
        s"Sending request: ${if (beforeCurlInsteadOfShow) request.toCurl
        else request.show(includeBody = logRequestBody, logRequestHeaders, sensitiveHeaders)}"
      }
    )

  override def response(
      request: Request[_, _],
      response: Response[_],
      responseBody: Option[String],
      elapsed: Option[Duration]
  ): F[Unit] =
    logger(
      responseLogLevel(response.code), {
        val responseAsString =
          response
            .copy(body = responseBody.getOrElse(""))
            .show(responseBody.isDefined, logResponseHeaders, sensitiveHeaders)
        s"Request: ${request.showBasic}${took(elapsed)}, response: $responseAsString"
      }
    )

  override def requestException(request: Request[_, _], elapsed: Option[Duration], e: Exception): F[Unit] = {
    @tailrec def findHttpError(exception: Throwable): Option[HttpError[_]] =
      Option(exception) match {
        case Some(error: HttpError[_]) => Some(error)
        case Some(_)                   => findHttpError(exception.getCause)
        case None                      => Option.empty
      }

    val logLevel = findHttpError(e) match {
      case Some(HttpError(_, statusCode)) =>
        responseLogLevel(statusCode)
      case _ =>
        responseExceptionLogLevel
    }
    logger(logLevel, s"Exception when sending request: ${request.showBasic}${took(elapsed)}", e)
  }

  private def took(elapsed: Option[Duration]): String = elapsed.fold("")(e => f", took: ${e.toMillis / 1000.0}%.3fs")
}

object DefaultLog {
  def defaultResponseLogLevel(c: StatusCode): LogLevel =
    if (c.isClientError || c.isServerError) LogLevel.Warn else LogLevel.Debug
}
