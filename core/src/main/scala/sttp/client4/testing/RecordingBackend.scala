package sttp.client4.testing

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import sttp.client4._
import sttp.monad.syntax._

import scala.util.{Failure, Success, Try}
import sttp.capabilities.Effect
import sttp.client4.wrappers.DelegateBackend

trait RecordingBackend {
  type RequestAndResponse = (GenericRequest[?, ?], Try[Response[?]])
  def allInteractions: List[RequestAndResponse]
}

abstract class AbstractRecordingBackend[F[_], P](delegate: GenericBackend[F, P])
    extends DelegateBackend[F, P](delegate)
    with RecordingBackend {

  private val _allInteractions = new AtomicReference[Vector[RequestAndResponse]](Vector())

  private def addInteraction(request: GenericRequest[?, ?], response: Try[Response[?]]): Unit =
    _allInteractions.updateAndGet(new UnaryOperator[Vector[RequestAndResponse]] {
      override def apply(t: Vector[RequestAndResponse]): Vector[RequestAndResponse] = t.:+((request, response))
    })

  override def send[T](request: GenericRequest[T, P & Effect[F]]): F[Response[T]] =
    delegate
      .send(request)
      .map { response =>
        addInteraction(request, Success(response))
        response
      }
      .handleError { case e: Exception =>
        addInteraction(request, Failure(e))
        monad.error(e)
      }

  override def allInteractions: List[RequestAndResponse] = _allInteractions.get().toList
}

object RecordingBackend {
  def apply(delegate: SyncBackend): SyncBackend & RecordingBackend =
    new AbstractRecordingBackend(delegate) with SyncBackend {}

  def apply[F[_]](delegate: Backend[F]): Backend[F] & RecordingBackend =
    new AbstractRecordingBackend(delegate) with Backend[F] {}

  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] & RecordingBackend =
    new AbstractRecordingBackend(delegate) with WebSocketBackend[F] {}

  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] & RecordingBackend =
    new AbstractRecordingBackend(delegate) with StreamBackend[F, S] {}

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] & RecordingBackend =
    new AbstractRecordingBackend(delegate) with WebSocketStreamBackend[F, S] {}
}
