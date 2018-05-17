package com.softwaremill.sttp.testing

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.timers.setTimeout

object Platform {

  def delayedFuture[T](delay: FiniteDuration)(result: => T): Future[T] = {
    val p = Promise[T]()

    setTimeout(delay)(p.success(result))
    p.future
  }
}
