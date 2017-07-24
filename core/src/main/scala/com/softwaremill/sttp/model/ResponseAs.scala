package com.softwaremill.sttp.model

import java.net.URLDecoder

import scala.collection.immutable.Seq

/**
  * @tparam T Target type as which the response will be read.
  * @tparam S If `T` is a stream, the type of the stream. Otherwise, `Nothing`.
  */
sealed trait ResponseAs[T, +S] {
  def map[T2](f: T => T2): ResponseAs[T2, S] =
    MappedResponseAs[T, T2, S](this, f)
}

case object IgnoreResponse extends ResponseAs[Unit, Nothing]
case class ResponseAsString(encoding: String)
    extends ResponseAs[String, Nothing]
case object ResponseAsByteArray extends ResponseAs[Array[Byte], Nothing]
case class ResponseAsParams(encoding: String)
    extends ResponseAs[Seq[(String, String)], Nothing] {

  private[sttp] def parse(s: String): Seq[(String, String)] = {
    s.split("&")
      .toList
      .flatMap(kv =>
        kv.split("=", 2) match {
          case Array(k, v) =>
            Some(
              (URLDecoder.decode(k, encoding), URLDecoder.decode(v, encoding)))
          case _ => None
      })
  }
}
case class ResponseAsStream[T, S]()(implicit val responseIsStream: S =:= T)
    extends ResponseAs[T, S]
case class MappedResponseAs[T, T2, S](raw: ResponseAs[T, S], g: T => T2)
    extends ResponseAs[T2, S] {
  override def map[T3](f: T2 => T3): ResponseAs[T3, S] =
    MappedResponseAs[T, T3, S](raw, g andThen f)
}
