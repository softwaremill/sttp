package sttp.client4.wrappers

import sttp.client4.monad.FunctionK
import sttp.client4.{
  Backend,
  Identity,
  StreamBackend,
  SyncBackend,
  WebSocketBackend,
  WebSocketStreamBackend,
  WebSocketSyncBackend
}
import sttp.monad.TryMonad

import scala.util.Try

/** A synchronous backend that safely wraps exceptions in `Try`'s */
object TryBackend {
  def apply(backend: SyncBackend): Backend[Try] =
    MappedEffectBackend(backend, idToTry, tryToId, TryMonad)
  def apply(backend: WebSocketBackend[Identity]): WebSocketBackend[Try] =
    MappedEffectBackend(backend, idToTry, tryToId, TryMonad)
  def apply(backend: WebSocketSyncBackend): WebSocketBackend[Try] =
    MappedEffectBackend(backend, idToTry, tryToId, TryMonad)
  def apply[S](backend: StreamBackend[Identity, S]): StreamBackend[Try, S] =
    MappedEffectBackend(backend, idToTry, tryToId, TryMonad)
  def apply[S](backend: WebSocketStreamBackend[Identity, S]): WebSocketStreamBackend[Try, S] =
    MappedEffectBackend(backend, idToTry, tryToId, TryMonad)

  private val tryToId: FunctionK[Try, Identity] =
    new FunctionK[Try, Identity] {
      override def apply[A](fa: => Try[A]): Identity[A] = fa.get
    }

  private val idToTry: FunctionK[Identity, Try] =
    new FunctionK[Identity, Try] {
      override def apply[A](fa: => Identity[A]): Try[A] = Try(fa)
    }
}
