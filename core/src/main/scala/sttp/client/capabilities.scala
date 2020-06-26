package sttp.client

class Streams[S] { // TODO: add constraint S <: Streams[S]?
  type BinaryStream
}

trait Effect[F[_]]

trait WebSockets
