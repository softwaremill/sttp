package sttp.client.httpclient.monix

import java.util.concurrent.Flow

import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.execution.atomic.Atomic
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.{Future, Promise}

// based on monix.reactive.Observable.toReactivePublisher
private[monix] final class FlowSubscriberAsMonixSubscriber[A] private (
    subscriber: Flow.Subscriber[A],
    subscription: Cancelable
)(implicit val scheduler: Scheduler)
    extends Subscriber[A]
    with Cancelable { self =>

  import FlowSubscriberAsMonixSubscriber._

  if (subscriber == null) throw null

  private[this] var isComplete = false
  private[this] val requests = new RequestsQueue
  private[this] var leftToPush = 0L
  private[this] var firstEvent = true
  private[this] var ack: Future[Ack] = Continue

  def cancel(): Unit = {
    requests.cancel()
    subscription.cancel()
  }

  @tailrec
  def onNext(elem: A): Future[Ack] = {
    if (isComplete)
      Stop
    else if (firstEvent) {
      firstEvent = false
      subscriber.onSubscribe(createSubscription())
      onNext(elem) // retry
    } else if (leftToPush > 0) {
      leftToPush -= 1
      subscriber.onNext(elem)
      ack = Continue
      ack
    } else {
      ack = requests.await().flatMap { requested =>
        if (requested <= 0) Stop
        else {
          leftToPush += (requested - 1)
          subscriber.onNext(elem)
          Continue
        }
      }

      ack
    }
  }

  def onError(ex: Throwable): Unit =
    if (!isComplete) {
      isComplete = true
      if (firstEvent) subscriber.onSubscribe(createSubscription())
      subscriber.onError(ex)
    }

  def onComplete(): Unit =
    if (!isComplete) {
      isComplete = true
      if (firstEvent) subscriber.onSubscribe(createSubscription())
      ack.syncOnContinue(subscriber.onComplete())
    }

  private def createSubscription(): Flow.Subscription = new Flow.Subscription {
    def cancel(): Unit = self.cancel()

    def request(n: Long): Unit = {
      try requests.request(n)
      catch {
        case ex: IllegalArgumentException =>
          subscriber.onError(ex)
      }
    }
  }
}

private[monix] object FlowSubscriberAsMonixSubscriber {

  /** Given an `org.reactivestreams.Subscriber` as defined by
    * the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification, it builds an [[monix.reactive.Observer]]
    * instance compliant with the Monix Rx implementation.
    */
  def apply[A](subscriber: Flow.Subscriber[A], subscription: Cancelable)(
      implicit s: Scheduler
  ): FlowSubscriberAsMonixSubscriber[A] =
    new FlowSubscriberAsMonixSubscriber[A](subscriber, subscription)

  /** An asynchronous queue implementation for dealing with
    * requests from a Subscriber.
    */
  private final class RequestsQueue {
    private[this] val state = Atomic(ActiveState(Queue.empty, Queue.empty): State)

    @tailrec
    def await(): Future[Long] = {
      state.get match {
        case CancelledState =>
          Future.successful(0)

        case oldState @ ActiveState(elements, promises) =>
          if (elements.nonEmpty) {
            val (e, newQ) = elements.dequeue
            val newState = ActiveState(newQ, promises)

            if (!state.compareAndSet(oldState, newState))
              await()
            else
              Future.successful(e)
          } else {
            val p = Promise[Long]()
            val newState = ActiveState(elements, promises.enqueue(p))

            if (!state.compareAndSet(oldState, newState))
              await()
            else
              p.future
          }
      }
    }

    @tailrec
    def request(n: Long): Unit = {
      require(
        n > 0,
        "n must be strictly positive, according to " +
          "the Reactive Streams contract, rule 3.9"
      )

      state.get match {
        case CancelledState =>
          () // do nothing

        case oldState @ ActiveState(elements, promises) if promises.nonEmpty =>
          val (p, q) = promises.dequeue
          val newState = ActiveState(elements, q)

          if (!state.compareAndSet(oldState, newState))
            request(n)
          else
            p.success(n)

        case oldState @ ActiveState(Queue(requested), promises) if requested > 0 =>
          val r = requested + n
          val newState = ActiveState(Queue(r), promises)

          if (!state.compareAndSet(oldState, newState))
            request(n)

        case oldState @ ActiveState(elements, promises) =>
          val newState = ActiveState(elements.enqueue(n), promises)
          if (!state.compareAndSet(oldState, newState))
            request(n)
      }
    }

    @tailrec
    def cancel(): Unit = {
      state.get match {
        case CancelledState =>
          () // do nothing

        case oldState @ ActiveState(_, promises) =>
          if (!state.compareAndSet(oldState, CancelledState))
            cancel()
          else
            promises.foreach(_.success(0))
      }
    }

    sealed trait State

    case class ActiveState(elements: Queue[Long], promises: Queue[Promise[Long]]) extends State

    case object CancelledState extends State
  }
}
