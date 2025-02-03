package sttp.client4.listener

import sttp.monad.MonadError
import sttp.client4.GenericRequest
import sttp.shared.Identity
import sttp.model.ResponseMetadata
import sttp.client4.ResponseException

/** A listener to be used by the [[ListenerBackend]] to get notified on request lifecycle events.
  *
  * @tparam L
  *   Type of a value ("tag") that is associated with a request, and passed the response (or exception) is available.
  *   Use `Unit` if no special value should be associated with a request.
  */
trait RequestListener[F[_], L] {

  /** Called before a request is sent. */
  def before[T, R](request: GenericRequest[T, R]): F[L]

  /** Called when the response body has been fully received (see [[sttp.client4.Request#onBodyReceived]]), but not yet
    * fully handled (e.g. parsed).
    *
    * This method is not called when there's an exception while reading the response body, decompressing, or for
    * WebSocket requests.
    *
    * Note that this method must run any effects immediately, as it returns a `Unit`, without the `F` wrapper.
    */
  def responseBodyReceived(request: GenericRequest[_, _], response: ResponseMetadata, tag: L): Unit

  /** Called when the request has been successfully handled, as specified by the response description.
    *
    * The [[responseBodyReceived]] might be called before this method (for safe, non-WebSocket requests), after (for
    * requests with `...Unsafe` response descriptions), or not at all (for WebSocket requests).
    *
    * @param exception
    *   A [[ResponseException]] that might occur when handling the response: when the raw response is successfully
    *   received via the network, but e.g. a parsing or decompression exception occurs.
    */
  def responseHandled(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      tag: L,
      exception: Option[ResponseException[_]]
  ): F[Unit]

  /** Called when there's an exception, other than [[ResponseException]] (then, response metadata is available), when
    * receiving the response body or handling the response (decompression, parsing).
    *
    * The [[responseBodyReceived]] might have been called before this method, but will not be called after.
    *
    * For [[ResponseException]]s, [[requestSuccessful]] is called instead.
    *
    * @param responseBodyReceivedCalled
    *   Indicates if [[responseBodyReceivedCalled]] has been called before this method.
    */
  def exception(
      request: GenericRequest[_, _],
      tag: L,
      exception: Throwable,
      responseBodyReceivedCalled: Boolean
  ): F[Unit]
}

object RequestListener {
  def lift[F[_], L](delegate: RequestListener[Identity, L], monadError: MonadError[F]): RequestListener[F, L] =
    new RequestListener[F, L] {
      override def before[T, R](request: GenericRequest[T, R]): F[L] =
        monadError.eval(delegate.before(request))

      override def responseBodyReceived(request: GenericRequest[?, ?], response: ResponseMetadata, tag: L): Unit =
        delegate.responseBodyReceived(request, response, tag)

      override def responseHandled(
          request: GenericRequest[_, _],
          response: ResponseMetadata,
          tag: L,
          e: Option[ResponseException[_]]
      ): F[Unit] =
        monadError.eval(delegate.responseHandled(request, response, tag, e))

      override def exception(
          request: GenericRequest[_, _],
          tag: L,
          e: Throwable,
          responseBodyReceivedCalled: Boolean
      ): F[Unit] =
        monadError.eval(delegate.exception(request, tag, e, responseBodyReceivedCalled))
    }
}
