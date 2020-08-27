package sttp.client.logging.slf4j

import org.slf4j.LoggerFactory

private[sttp] class Logger(private val name: String) {
  private val underlying = LoggerFactory.getLogger(name)

  def debug(message: => String): Unit =
    if (underlying.isDebugEnabled) underlying.debug(message) else ()
  def debug(message: => String, t: Throwable): Unit =
    if (underlying.isDebugEnabled) underlying.debug(message, t) else ()

  def info(message: => String): Unit =
    if (underlying.isInfoEnabled) underlying.info(message) else ()
  def info(message: => String, t: Throwable): Unit =
    if (underlying.isInfoEnabled) underlying.info(message, t) else ()

  def warn(message: => String): Unit =
    if (underlying.isWarnEnabled) underlying.error(message) else ()
  def warn(message: => String, t: Throwable): Unit =
    if (underlying.isWarnEnabled) underlying.error(message, t) else ()

  def error(message: => String): Unit =
    if (underlying.isErrorEnabled) underlying.error(message) else ()
  def error(message: => String, t: Throwable): Unit =
    if (underlying.isErrorEnabled) underlying.error(message, t) else ()
}
