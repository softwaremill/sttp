package sttp.client4.internal.httpclient

import sttp.client4.Identity

import java.util.concurrent.Semaphore
import scala.concurrent.blocking

private[client4] class IdSequencer extends Sequencer[Identity] {
  private val semaphore = new Semaphore(1)

  def apply[T](t: => Identity[T]): Identity[T] = {
    blocking {
      semaphore.acquire()
    }
    val result = t
    semaphore.release()
    result
  }
}
