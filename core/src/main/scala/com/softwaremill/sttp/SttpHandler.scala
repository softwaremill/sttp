package com.softwaremill.sttp

import com.softwaremill.sttp.model.ResponseAs

import scala.language.higherKinds

trait SttpHandler[R[_], -S] {
  def send[T](request: Request, responseAs: ResponseAs[T, S]): R[Response[T]]
}
