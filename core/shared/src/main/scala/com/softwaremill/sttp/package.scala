package com.softwaremill

package object sttp extends SttpApi {
  type Id[X] = X
  type Empty[X] = None.type

  type PartialRequest[T, +S] = RequestT[Empty, T, S]
  type Request[T, +S] = RequestT[Id, T, S]

  /**
    * Provide an implicit value of this type to serialize arbitrary classes into a request body.
    * Backends might also provide special logic for serializer instances which they define (e.g. to handle streaming).
    */
  type BodySerializer[B] = B => BasicRequestBody
}