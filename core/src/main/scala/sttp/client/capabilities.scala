package sttp.client

/**
  * A capability of sending and receiving streaming bodies.
  * @tparam S The type to use as a capability. Should be the self-type of the implementation. This is needed so that
  *           capabilities are expressed in terms of class types, not singleton object types.
  */
class Streams[S] {
  type BinaryStream
  type Pipe[_, _]
}

/**
  * A capability of supporting the given effect type, such as [[Identity]] or [[scala.concurrent.Future]].
  * Used only as a type parameter, without implementations.
  */
trait Effect[F[_]]

/**
  * A capability of sending websocket requests.
  * Used only as a type parameter, without implementations.
  */
trait WebSockets
