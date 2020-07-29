package sttp.client

import sttp.client.monad.MonadError

/**
  * @note Backends should try to classify exceptions into one of the categories specified by [[SttpClientException]].
  *       Other exceptions should be thrown unchanged.
  * @tparam F The type constructor in which responses are wrapped. E.g. [[Identity]]
  *           for synchronous backends, [[scala.concurrent.Future]] for asynchronous backends.
  * @tparam P TODO (supported capabilities, above Effect[F])
  */
trait SttpBackend[F[_], +P] {

  /**
    * @tparam R The capabilities required by the request. This can include the capabilities supported by the backend,
    *           which always includes `Effect[F]`.
    */
  def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]]

  def close(): F[Unit]

  /**
    * The effect wrapper for responses. Allows writing wrapper backends, which map/flatMap over
    * the return value of [[send]].
    */
  def responseMonad: MonadError[F]
}
