package sttp.client

/**
  * @tparam S The type to use as a capability. Should be the self-type of the implementation. This is needed so that
  *           capabilities are expressed in terms of class types, not singleton object types.
  */
class Streams[S] {
  type BinaryStream
  type Pipe[_, _]
}

trait Effect[F[_]]

trait WebSockets

trait HasWebsockets[R] { def value: Boolean }
object HasWebsockets {
  implicit def hasWebSockets[R <: WebSockets]: HasWebsockets[R] = new HasWebsockets[R] { def value = true }
  implicit def hasNoWebSockets[R]: HasWebsockets[R] = new HasWebsockets[R] { def value = false }
}
