package com.softwaremill.sttp

import scala.language.higherKinds

trait SttpHandler[R[_]] {
  def send[T](request: Request, responseAs: ResponseAs[T]): R[Response[T]]
}

trait SttpStreamHandler[R[_], S] extends SttpHandler[R] {
  def send[T](request: Request, responseAsStream: ResponseAsStream[S]): R[Response[S]]
  def sendStream[T](request: Request, contentType: String, stream: S, responseReader: ResponseAs[T]): R[Response[T]]
}