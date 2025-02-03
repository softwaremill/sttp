package sttp.client4.listener

import sttp.monad.MonadError
import sttp.client4.GenericRequest
import sttp.shared.Identity
import sttp.model.ResponseMetadata
import sttp.client4.ResponseException

/** A listener to be used by the [[ListenerBackend]] to get notified on request lifecycle events. The request can be
  * enriched before being sent, e.g. to capture additional metrics.
  *
  * @tparam L
  *   Type of a value ("tag") that is associated with a request, and passed the response (or exception) is available.
  *   Use `Unit` if no special value should be associated with a request.
  */
trait RequestListener[F[_], L] {
  def beforeRequest[T, R](request: GenericRequest[T, R]): F[(GenericRequest[T, R], L)]
  def requestException(request: GenericRequest[_, _], tag: L, e: Throwable): F[Unit]

  /** @param e
    *   A [[ResponseException]] that might occur when handling the response: when the raw response is successfully
    *   received via the network, but e.g. a parsing or decompression exception occurs.
    */
  def requestSuccessful(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      tag: L,
      e: Option[ResponseException[_]]
  ): F[Unit]
}

object RequestListener {
  def lift[F[_], L](delegate: RequestListener[Identity, L], monadError: MonadError[F]): RequestListener[F, L] =
    new RequestListener[F, L] {
      override def beforeRequest[T, R](request: GenericRequest[T, R]): F[(GenericRequest[T, R], L)] =
        monadError.eval(delegate.beforeRequest(request))
      override def requestException(request: GenericRequest[_, _], tag: L, e: Throwable): F[Unit] =
        monadError.eval(delegate.requestException(request, tag, e))
      override def requestSuccessful(
          request: GenericRequest[_, _],
          response: ResponseMetadata,
          tag: L,
          e: Option[ResponseException[_]]
      ): F[Unit] =
        monadError.eval(delegate.requestSuccessful(request, response, tag, e))
    }
}
