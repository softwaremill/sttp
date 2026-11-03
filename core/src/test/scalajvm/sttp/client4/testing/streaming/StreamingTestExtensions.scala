package sttp.client4.testing.streaming

import org.scalatest.freespec.AsyncFreeSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.testing.HttpTest.endpoint

import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory, TimeUnit, TimeoutException}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait StreamingTestExtensions[F[_], S] extends AsyncFreeSpecLike with Matchers { self: StreamingTest[F, S] =>

  /** Fails if `f` doesn't complete within 10 seconds. Unlike `timeoutToNone`, doesn't wait for any cancellation to
    * complete, so that a backend which doesn't cancel properly fails the test, instead of hanging it.
    */
  private def withDeadline[T](f: Future[T], waitingFor: String): Future[T] = {
    val result = Promise[T]()
    val timeout = StreamingTestExtensions.scheduler.schedule(
      new Runnable {
        override def run(): Unit = { result.tryFailure(new TimeoutException(s"Timed out waiting for $waitingFor")); () }
      },
      10,
      TimeUnit.SECONDS
    )
    f.onComplete { r =>
      timeout.cancel(false)
      result.tryComplete(r)
    }
    result.future
  }

  if (supportsCancellation) {
    "cancel" - {
      "a response stream while waiting for data" in {
        // the endpoint sends one chunk and then nothing, so reading the body only ends when it's cancelled; see #2963
        val request: StreamRequest[streams.BinaryStream, S] =
          basicRequest.get(uri"$endpoint/streaming/stall").response(asStreamAlwaysUnsafe(streams))

        withDeadline(request.send(backend).toFuture(), "the response")
          .flatMap(response => withDeadline(timeoutToNone(bodyConsumer(response.body), 500).toFuture(), "cancellation"))
          .map(_ shouldBe None)
      }

      "a request while streaming the request body" in {
        val started = Promise[Unit]()
        val finalized = Promise[Unit]()
        val body = neverEndingBodyProducer(
          "a".getBytes,
          () => { started.trySuccess(()); () },
          () => { finalized.trySuccess(()); () }
        )
        val sending = basicRequest.post(uri"$endpoint/streaming/echo").streamBody(streams)(body).send(backend)

        for {
          result <- withDeadline(timeoutToNone(sending, 1000).toFuture(), "cancellation")
          _ = result shouldBe None
          _ <- withDeadline(started.future, "the request body to start")
          _ <- withDeadline(finalized.future, "the request body to be finalized")
        } yield succeed
      }
    }
  }

  "fail the request when the request body stream fails" in {
    val error = new RuntimeException("request body failure")
    val sending = basicRequest
      .post(uri"$endpoint/streaming/echo")
      .streamBody(streams)(failingBodyProducer("a".getBytes, error))
      .send(backend)

    withDeadline(sending.toFuture(), "the request to fail").transform(Success(_)).map {
      case Failure(e) => Iterator.iterate(e)(_.getCause).takeWhile(_ != null).toList should contain(error)
      case Success(r) => fail(s"Expected the request to fail, got: $r")
    }
  }
}

object StreamingTestExtensions {
  private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r, "streaming-test-deadline")
      t.setDaemon(true)
      t
    }
  })
}
