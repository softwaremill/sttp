package sttp.client

import sttp.client.monad.{EitherMonad, MonadError}

import scala.util.control.NonFatal

/** A synchronous backend that safely wraps [[SttpBackend]] exceptions in `Either[Throwable, ?]`'s
  *
  * @param delegate A synchronous `SttpBackend` which to which this backend forwards all requests
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  */
class EitherBackend[S](delegate: SttpBackend[Identity, S]) extends SttpBackend[Either[Throwable, ?], S] {

  override def send[T](request: Request[T, S]): Either[Throwable, Response[T]] = doTry(delegate.send(request))

  override def close(): Either[Throwable, Unit] = doTry(delegate.close())

  private def doTry[T](t: => T): Either[Throwable, T] = {
    try Right(t)
    catch {
      case NonFatal(e) => Left(e)
    }
  }

  override def responseMonad: MonadError[Either[Throwable, ?]] = EitherMonad
}
