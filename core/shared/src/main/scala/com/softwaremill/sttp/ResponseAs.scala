package com.softwaremill.sttp

import java.net.URLDecoder

import scala.collection.immutable.Seq
import scala.language.higherKinds
import scala.util.Try

import com.softwaremill.sttp.file.File

/**
  * @tparam T Target type as which the response will be read.
  * @tparam S If `T` is a stream, the type of the stream. Otherwise, `Nothing`.
  */
sealed trait ResponseAs[T, +S] {
  def map[T2](f: T => T2): ResponseAs[T2, S]
}

/**
  * Response handling specification which isn't derived from another response
  * handling method, but needs to be handled directly by the backend.
  */
sealed trait BasicResponseAs[T, +S] extends ResponseAs[T, S] {
  override def map[T2](f: (T) => T2): ResponseAs[T2, S] =
    MappedResponseAs[T, T2, S](this, f)
}

case object IgnoreResponse extends BasicResponseAs[Unit, Nothing]
case class ResponseAsString(encoding: String) extends BasicResponseAs[String, Nothing]
case object ResponseAsByteArray extends BasicResponseAs[Array[Byte], Nothing]
case class ResponseAsStream[T, S]()(implicit val responseIsStream: S =:= T) extends BasicResponseAs[T, S]

case class MappedResponseAs[T, T2, S](raw: BasicResponseAs[T, S], g: T => T2) extends ResponseAs[T2, S] {
  override def map[T3](f: T2 => T3): ResponseAs[T3, S] =
    MappedResponseAs[T, T3, S](raw, g andThen f)
}

case class ResponseAsFile(output: File, overwrite: Boolean) extends BasicResponseAs[File, Nothing]

object ResponseAs {
  private[sttp] def parseParams(s: String, encoding: String): Seq[(String, String)] = {
    s.split("&")
      .toList
      .flatMap(kv =>
        kv.split("=", 2) match {
          case Array(k, v) =>
            Some((URLDecoder.decode(k, encoding), URLDecoder.decode(v, encoding)))
          case _ => None
      })
  }

  /**
    * Handles responses according to the given specification when basic
    * response specifications can be handled eagerly, that is without
    * wrapping the result in the target monad (`handleBasic` returns
    * `Try[T]`, not `R[T]`).
    */
  private[sttp] trait EagerResponseHandler[S] {
    def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T]

    def handle[T, R[_]](responseAs: ResponseAs[T, S], responseMonad: MonadError[R]): R[T] = {

      responseAs match {
        case mra @ MappedResponseAs(raw, g) =>
          responseMonad.map(responseMonad.fromTry(handleBasic(mra.raw)))(mra.g)
        case bra: BasicResponseAs[T, S] =>
          responseMonad.fromTry(handleBasic(bra))
      }
    }
  }
}
