package sttp.client3.logging

case class LoggingOptions(
    logRequestBody: Option[Boolean] = None,
    logResponseBody: Option[Boolean] = None,
    logRequestHeaders: Option[Boolean] = None,
    logResponseHeaders: Option[Boolean] = None
)
