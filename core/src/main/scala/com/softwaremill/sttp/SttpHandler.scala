package com.softwaremill.sttp

import scala.language.higherKinds
import scala.concurrent.duration._

/**
  * @tparam R The type constructor in which responses are wrapped. E.g. `Id`
  *           for synchronous handlers, `Future` for asynchronous handlers.
  * @tparam S The type of streams that are supported by the handler. `Nothing`,
  *           if streaming requests/responses is not supported by this handler.
  */
trait SttpHandler[R[_], -S] {
  def send[T](request: Request[T, S]): R[Response[T]]

  def close(): Unit

  /**
    * The monad in which the responses are wrapped. Allows writing wrapper
    * handlers, which map/flatMap over the return value of [[send]].
    */
  def responseMonad: MonadError[R]
}

object SttpHandler {
  private[sttp] val DefaultConnectionTimeout = 30.seconds
}
