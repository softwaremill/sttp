package sttp.client4.testing

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import sttp.client4._
import sttp.monad.syntax._

import scala.util.{Failure, Success, Try}
import sttp.capabilities.Effect

trait RecordingBackend {
  type RequestAndResponse = (GenericRequest[_, _], Try[Response[_]])
  def allInteractions: List[RequestAndResponse]
}

abstract class AbstractRecordingBackend[F[_], P](delegate: GenericBackend[F, P])
    extends DelegateBackend[F, P](delegate)
    with RecordingBackend {

  private val _allInteractions = new AtomicReference[Vector[RequestAndResponse]](Vector())

  private def addInteraction(request: GenericRequest[_, _], response: Try[Response[_]]): Unit = {
    _allInteractions.updateAndGet(new UnaryOperator[Vector[RequestAndResponse]] {
      override def apply(t: Vector[RequestAndResponse]): Vector[RequestAndResponse] = t.:+((request, response))
    })
  }

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
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
  }

  override def allInteractions: List[RequestAndResponse] = _allInteractions.get().toList
}

object RecordingBackend {
  def apply(delegate: SyncBackend): SyncBackend with RecordingBackend =
    new AbstractRecordingBackend(delegate) with SyncBackend {}

  def apply[F[_]](delegate: Backend[F]): Backend[F] with RecordingBackend =
    new AbstractRecordingBackend(delegate) with Backend[F] {}

  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] with RecordingBackend =
    new AbstractRecordingBackend(delegate) with WebSocketBackend[F] {}

  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] with RecordingBackend =
    new AbstractRecordingBackend(delegate) with StreamBackend[F, S] {}

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] with RecordingBackend =
    new AbstractRecordingBackend(delegate) with WebSocketStreamBackend[F, S] {}
}
