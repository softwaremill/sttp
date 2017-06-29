package com.softwaremill.sttp

import com.softwaremill.sttp.model.{ResponseAs, ResponseAsStream}

import scala.language.higherKinds

trait SttpHandler[R[_]] {
  def send[T](request: Request, responseAs: ResponseAs[T]): R[Response[T]]
}

trait SttpStreamHandler[R[_], S] extends SttpHandler[R] {
  def send(request: Request, responseAsStream: ResponseAsStream[S]): R[Response[S]]
  def sendStream[T](request: Request, contentType: String, stream: S, responseAs: ResponseAs[T]): R[Response[T]]
  def sendStream(request: Request, contentType: String, stream: S, responseAsStream: ResponseAsStream[S]): R[Response[S]]
}