package com.softwaremill.sttp

import com.softwaremill.sttp.model.{ResponseAs, ResponseAsStream}

import scala.language.higherKinds

trait SttpHandler[R[_], +S, -AcceptsResponseAs[x, -s] <: ResponseAs[x, s]] {
  def send[T](request: Request, responseAs: AcceptsResponseAs[T, S]): R[Response[T]]
}

//trait SttpStreamHandler[R[_], S] extends SttpHandler[R] {
//  def send(request: Request, responseAsStream: ResponseAsStream[S]): R[Response[S]]
//}


/*

Cat <: Animal
Dog <: Animal

x: Animal := Cat

Contravariant:
def eat(x: Cat) := def eat(x: Animal)
Animal => Cat := Animal => Unit

Covariant:
def create: Animal := def create: Cat
Unit => Animal := Unit => Cat

---

RA >: RAS
Handler[RAS] >: Handler[RA]

 */