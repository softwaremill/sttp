import java.io.FileNotFoundException
import java.net.{ConnectException, URL}

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

object PollingUtils {

  def waitUntilServerAvailable(url: URL): Unit = {
    val connected = poll(5.seconds, 250.milliseconds)({
      urlConnectionAvailable(url)
    })
    if (!connected) {
      throw new TimeoutException(s"Failed to connect to $url")
    }
  }

  def poll(timeout: FiniteDuration, interval: FiniteDuration)(poll: => Boolean): Boolean = {
    val start = System.nanoTime()

    def go(): Boolean = {
      if (poll) {
        true
      } else if ((System.nanoTime() - start) > timeout.toNanos) {
        false
      } else {
        Thread.sleep(interval.toMillis)
        go()
      }
    }
    go()
  }

  def urlConnectionAvailable(url: URL): Boolean = {
    try {
      url.openConnection()
        .getInputStream
        .close()
      true
    } catch {
      case _: ConnectException => false
      case _: FileNotFoundException => true // on 404
    }
  }
}
