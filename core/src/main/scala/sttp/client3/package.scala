package sttp

package object client3 extends SttpApi {
  type Identity[X] = X

  /** Provide an implicit value of this type to serialize arbitrary classes into a request body.
    * Backends might also provide special logic for serializer instances which they define (e.g. to handle streaming).
    */
  type BodySerializer[B] = B => BasicRequestBody

  type RetryWhen = (Request[_, _], Either[Throwable, Response[_]]) => Boolean

  @deprecated(message = "use ResponseException", since = "3.0.0")
  type ResponseError[+HE, +DE] = ResponseException[HE, DE]
  @deprecated(message = "use DeserializationException", since = "3.0.0")
  type DeserializationError[DE] = DeserializationException[DE]
}
