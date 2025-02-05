package sttp.client4.testing

/** Describes how the `v` value should be handled by a [[StubBackend]]: whether there should be an attempt to adjust it
  * to the desired type, or if the body should be returned exactly as provided.
  *
  * For [[StubBody.Adjust]], the following base types are adjusted, depending on the response description:
  *
  *   - strings, byte arrays and input streams
  *   - files
  *   - [[sttp.ws.WebSocket]] and [[sttp.ws.testing.WebSocketStub]]
  *   - asynchronous, non-blocking byte streams, of type [[sttp.capabilities.Streams.BinaryStream]] (depending on the
  *     streams type used)
  *   - [[WebSocketStreamConsumer]]
  *
  * Note that providing the stub body is not type-safe: the stub doesn't have a way to check if the type of the body in
  * the configured response is the same as, or can be converted to, the one specified by the request; hence, a
  * [[ClassCastException]] might occur (in case of exact bodies and asynchronous streams), as well as
  * [[IllegalArgumentException]] in case adjustment is not possible.
  */
sealed trait StubBody {
  val v: Any
}
object StubBody {
  case class Adjust(v: Any) extends StubBody
  case class Exact(v: Any) extends StubBody
}
