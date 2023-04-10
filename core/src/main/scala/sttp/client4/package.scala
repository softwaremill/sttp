package sttp

package object client4 extends SttpApi {
  type Identity[+X] = X

  /** Provide an implicit value of this type to serialize arbitrary classes into a request body. Backends might also
    * provide special logic for serializer instances which they define (e.g. to handle streaming).
    */
  type BodySerializer[B] = B => BasicBodyPart

  type RetryWhen = (GenericRequest[_, _], Either[Throwable, Response[_]]) => Boolean
}
