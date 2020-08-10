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
