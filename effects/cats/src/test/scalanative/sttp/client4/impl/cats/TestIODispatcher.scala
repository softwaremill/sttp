package sttp.client4.impl.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import org.scalatest.{BeforeAndAfterAll, Suite}

trait TestIODispatcher extends BeforeAndAfterAll { this: Suite =>
  private val (d, _) = Dispatcher.parallel[IO].allocated.unsafeRunSync()
  protected var dispatcher: Dispatcher[IO] = d

  override protected def afterAll(): Unit = {
    super.afterAll()
  }
}