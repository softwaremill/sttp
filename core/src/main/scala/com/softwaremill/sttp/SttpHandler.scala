package com.softwaremill.sttp

import scala.language.higherKinds

trait SttpHandler[R[_], -S] {
  def send[T](request: Request[T, S]): R[Response[T]]
}
