package com.softwaremill.sttp

import com.softwaremill.sttp.model.ResponseAs

import scala.language.higherKinds

trait SttpHandler[R[_], -S, -AcceptsResponseAs[x, +s] <: ResponseAs[x, s]] {
  def send[T](request: Request, responseAs: AcceptsResponseAs[T, S]): R[Response[T]]
}
