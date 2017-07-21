package com.softwaremill.sttp

import scala.language.higherKinds

/**
  * @tparam R The type constructor in which responses are wrapped. E.g. `Id`
  *           for synchronous handlers, `Future` for asynchronous handlers.
  * @tparam S The type of streams that are supported by the handler. `Nothing`,
  *           if streaming requests/responses is not supported by this handler.
  */
trait SttpHandler[R[_], -S] {
  def send[T](request: Request[T, S]): R[Response[T]]
}
