package com.softwaremill.sttp

/**
  * Natural transformation, or higher-kinded function in general.
  * e.g. List ~> Option can be _.headOption
  * */
trait FunctionK[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A]
}
