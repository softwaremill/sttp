package sttp.client4.impl.cats

import cats.effect.IO
import cats.effect.unsafe.{IORuntime, IORuntimeConfig}
import org.scalatest.freespec.AsyncFreeSpec

class CatsMonadErrorTest extends AsyncFreeSpec {
  "blocking" - {
    "should shift to blocking execution context" in {
      implicit val ioRuntime: IORuntime = createIORuntime(computePoolSize = 1)
      val monad = new CatsMonadError[IO]

      val program = monad
        .blocking(Thread.sleep(100))
        .background
        .use(getOutcome => IO.race(getOutcome, IO.unit))

      program
        .flatMap(either => IO.delay(assert(either.isRight)))
        .unsafeToFuture()
    }
  }

  private def createIORuntime(computePoolSize: Int): IORuntime = {
    val (compute, _, _) = IORuntime.createWorkStealingComputeThreadPool(computePoolSize)
    val (blocking, _) = IORuntime.createDefaultBlockingExecutionContext()

    IORuntime(compute, blocking, compute, () => (), IORuntimeConfig())
  }
}
