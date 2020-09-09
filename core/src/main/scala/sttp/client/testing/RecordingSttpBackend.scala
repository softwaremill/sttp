package sttp.client.testing

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import sttp.capabilities.Effect
import sttp.client.{Request, Response, SttpBackend}
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.util.{Failure, Success, Try}

class RecordingSttpBackend[F[_], +P](delegate: SttpBackend[F, P]) extends SttpBackend[F, P] {
  type RequestAndResponse = (Request[_, _], Try[Response[_]])

  private val _allInteractions = new AtomicReference[Vector[RequestAndResponse]](Vector())

  private def addInteraction(request: Request[_, _], response: Try[Response[_]]): Unit = {
    _allInteractions.updateAndGet(new UnaryOperator[Vector[RequestAndResponse]] {
      override def apply(t: Vector[RequestAndResponse]): Vector[RequestAndResponse] = t.:+((request, response))
    })
  }

  override def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    implicit val m: MonadError[F] = responseMonad
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

  override def close(): F[Unit] = delegate.close()
  override def responseMonad: MonadError[F] = delegate.responseMonad

  def allInteractions: List[RequestAndResponse] = _allInteractions.get().toList
}
