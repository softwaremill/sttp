package sttp.client

import sttp.client.internal._
import sttp.client.monad.MonadError
import sttp.model.internal.Rfc3986

import scala.collection.immutable.Seq
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

/**
  * @tparam T Target type as which the response will be read.
  * @tparam S If `T` is a stream, the type of the stream. Otherwise, `Nothing`.
  */
sealed trait ResponseAs[T, +S] {
  def map[T2](f: T => T2): ResponseAs[T2, S] = mapWithMetadata { case (t, _) => f(t) }
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): ResponseAs[T2, S] = MappedResponseAs[T, T2, S](this, f)
}

/**
  * Response handling specification which isn't derived from another response
  * handling method, but needs to be handled directly by the backend.
  */
sealed trait BasicResponseAs[T, +S] extends ResponseAs[T, S]

case object IgnoreResponse extends BasicResponseAs[Unit, Nothing]
case object ResponseAsByteArray extends BasicResponseAs[Array[Byte], Nothing]
case class ResponseAsStream[T, S]()(implicit val responseIsStream: S =:= T) extends BasicResponseAs[T, S]
case class ResponseAsFile(output: SttpFile) extends BasicResponseAs[SttpFile, Nothing]

case class ResponseAsFromMetadata[T, S](f: ResponseMetadata => ResponseAs[T, S]) extends ResponseAs[T, S]

case class MappedResponseAs[T, T2, S](raw: ResponseAs[T, S], g: (T, ResponseMetadata) => T2) extends ResponseAs[T2, S] {
  override def mapWithMetadata[T3](f: (T2, ResponseMetadata) => T3): ResponseAs[T3, S] =
    MappedResponseAs[T, T3, S](raw, (t, h) => f(g(t, h), h))
}

object ResponseAs {
  implicit class RichResponseAsEither[L, R, S](ra: ResponseAs[Either[L, R], S]) {
    def mapLeft[L2](f: L => L2): ResponseAs[Either[L2, R], S] = ra.map(_.left.map(f))
    def mapRight[R2](f: R => R2): ResponseAs[Either[L, R2], S] = ra.map(_.right.map(f))
  }

  private[client] def parseParams(s: String, charset: String): Seq[(String, String)] = {
    s.split("&")
      .toList
      .flatMap(
        kv =>
          kv.split("=", 2) match {
            case Array(k, v) =>
              Some((Rfc3986.decode()(k, charset), Rfc3986.decode()(v, charset)))
            case _ => None
          }
      )
  }

  /**
    * Handles responses according to the given specification when basic
    * response specifications can be handled eagerly, that is without
    * wrapping the result in the target monad (`handleBasic` returns
    * `Try[T]`, not `R[T]`).
    */
  private[client] trait EagerResponseHandler[S] {
    def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T]

    def handle[T, F[_]](responseAs: ResponseAs[T, S], responseMonad: MonadError[F], meta: ResponseMetadata): F[T] = {

      responseAs match {
        case MappedResponseAs(raw, g) =>
          responseMonad.map(handle(raw, responseMonad, meta))(t => g(t, meta))
        case ResponseAsFromMetadata(f) => handle(f(meta), responseMonad, meta)
        case bra: BasicResponseAs[T, S] =>
          responseMonad.fromTry(handleBasic(bra))
      }
    }
  }

  /**
    * Returns a function, which maps `Left` values to [[HttpError]]s, and attempts to deserialize `Right` values using
    * the given function, catching any exceptions and representing them as [[DeserializationError]]s.
    */
  def deserializeRightCatchingExceptions[T](
      doDeserialize: String => T
  ): Either[String, String] => Either[ResponseError[Exception], T] = {
    case Left(s)  => Left(HttpError(s))
    case Right(s) => deserializeCatchingExceptions(doDeserialize)(s)
  }

  /**
    * Returns a function, which attempts to deserialize `Right` values using the given function, catching any
    * exceptions and representing them as [[DeserializationError]]s.
    */
  def deserializeCatchingExceptions[T](
      doDeserialize: String => T
  ): String => Either[DeserializationError[Exception], T] =
    deserializeWithError(
      (s: String) =>
        Try(doDeserialize(s)) match {
          case Failure(e: Exception) => Left(e)
          case Failure(t: Throwable) => throw t
          case Success(t)            => Right(t): Either[Exception, T]
        }
    )

  /**
    * Returns a function, which maps `Left` values to [[HttpError]]s, and attempts to deserialize `Right` values using
    * the given function.
    */
  def deserializeRightWithError[E, T](
      doDeserialize: String => Either[E, T]
  ): Either[String, String] => Either[ResponseError[E], T] = {
    case Left(s)  => Left(HttpError(s))
    case Right(s) => deserializeWithError(doDeserialize)(s)
  }

  /**
    * Converts a deserialization function, which returns errors of type `E`, into a function where errors are wrapped
    * using [[DeserializationError]].
    */
  def deserializeWithError[E, T](doDeserialize: String => Either[E, T]): String => Either[DeserializationError[E], T] =
    s =>
      doDeserialize(s) match {
        case Left(e)  => Left(DeserializationError(s, e))
        case Right(b) => Right(b)
      }
}

sealed abstract class ResponseError[+T] extends Exception
case class HttpError(body: String) extends ResponseError[Nothing]
case class DeserializationError[T](original: String, error: T) extends ResponseError[T]
