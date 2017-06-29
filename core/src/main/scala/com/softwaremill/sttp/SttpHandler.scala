package com.softwaremill.sttp

trait SttpHandler[R[_]] {
  def send[T](request: Request, responseReader: ResponseBodyReader[T]): R[Response[T]]
}

trait SttpStreamHandler[R[_], -S] extends SttpHandler[R] {
  def sendStream[T](request: Request, contentType: String, stream: S, responseReader: ResponseBodyReader[T]): R[Response[T]]
}