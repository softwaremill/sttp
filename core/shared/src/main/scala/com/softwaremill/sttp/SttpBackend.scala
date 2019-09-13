package com.softwaremill.sttp

import com.softwaremill.sttp.monad.MonadError

import scala.language.higherKinds

/**
  * @tparam R The type constructor in which responses are wrapped. E.g. `Id`
  *           for synchronous backends, `Future` for asynchronous backends.
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  */
trait SttpBackend[R[_], -S] {
  def send[T](request: Request[T, S]): R[Response[T]]

  def close(): Unit

  /**
    * The monad in which the responses are wrapped. Allows writing wrapper
    * backends, which map/flatMap over the return value of [[send]].
    */
  def responseMonad: MonadError[R]
}
