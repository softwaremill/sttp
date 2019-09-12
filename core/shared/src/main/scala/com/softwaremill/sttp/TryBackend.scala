package com.softwaremill.sttp

import scala.util.Try

/** A synchronous backend that safely wraps [[SttpBackend]] exceptions in `Try`'s
  *
  * @param delegate A synchronous `SttpBackend` which to which this backend forwards all requests
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  */
class TryBackend[S](delegate: SttpBackend[Identity, S]) extends SttpBackend[Try, S] {
  override def send[T](request: Request[T, S]): Try[Response[T]] =
    Try(delegate.send(request))

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[Try] = TryMonad
}
