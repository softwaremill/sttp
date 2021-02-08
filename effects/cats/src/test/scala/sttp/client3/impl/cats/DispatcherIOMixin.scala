package sttp.client3.impl.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import org.scalatest.{BeforeAndAfterAll, Suite}

trait DispatcherIOMixin extends BeforeAndAfterAll {
  this: Suite =>

  // use a var to avoid initialization error `scala.UninitializedFieldError`
  protected implicit var dispatcher: Dispatcher[IO] = _

  private val (d, shutdownDispatcher) = Dispatcher[IO].allocated.unsafeRunSync()
  dispatcher = d

  override protected def afterAll(): Unit = {
    shutdownDispatcher.unsafeRunSync()
    super.afterAll()
  }
}
