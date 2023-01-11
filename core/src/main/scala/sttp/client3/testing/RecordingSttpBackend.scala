package sttp.client3.testing

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import sttp.capabilities.Effect
import sttp.client3.{DelegateSttpBackend, AbstractRequest, Response, SttpBackend}
import sttp.monad.syntax._

import scala.util.{Failure, Success, Try}

class RecordingSttpBackend[F[_], +P](delegate: SttpBackend[F, P]) extends DelegateSttpBackend[F, P](delegate) {
  type RequestAndResponse = (AbstractRequest[_, _], Try[Response[_]])

  private val _allInteractions = new AtomicReference[Vector[RequestAndResponse]](Vector())

  private def addInteraction(request: AbstractRequest[_, _], response: Try[Response[_]]): Unit = {
    _allInteractions.updateAndGet(new UnaryOperator[Vector[RequestAndResponse]] {
      override def apply(t: Vector[RequestAndResponse]): Vector[RequestAndResponse] = t.:+((request, response))
    })
  }

  override def send[T, R >: P with Effect[F]](request: AbstractRequest[T, R]): F[Response[T]] = {
    delegate
      .send(request)
      .map { response =>
        addInteraction(request, Success(response))
        response
      }
      .handleError { case e: Exception =>
        addInteraction(request, Failure(e))
        responseMonad.error(e)
      }
  }

  def allInteractions: List[RequestAndResponse] = _allInteractions.get().toList
}
