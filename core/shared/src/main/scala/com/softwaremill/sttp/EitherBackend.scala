package com.softwaremill.sttp

import scala.util.control.NonFatal

/** A Backend that safely wraps SttpBackend exceptions in Either[Throwable, ?]'s
  *
  * @param delegate An SttpBackend which to which this backend forwards all requests
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  */
class EitherBackend[-S](delegate: SttpBackend[Id, S]) extends SttpBackend[Either[Throwable, ?], S] {

  override def send[T](request: Request[T, S]): Either[Throwable, Response[T]] =
    try Right(delegate.send(request))
    catch {
      case NonFatal(e) => Left(e)
    }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[Either[Throwable, ?]] = EitherMonad
}
