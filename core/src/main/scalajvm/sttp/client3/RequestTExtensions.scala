package sttp.client3

import sttp.capabilities.Effect

import java.io.File
import java.nio.file.Path
import sttp.client3.internal.{IsIdInRequest, SttpFile}

trait RequestTExtensions[U[_], T, -R] { self: RequestT[U, T, R] =>

  // although identical for each platform, this cannot be in RequestT because overloading breaks
  /** Sends the request, using the given backend. Only requests for which the method & URI are specified can be sent.
    *
    * The required capabilities must be a subset of the capabilities provided by the backend.
    *
    * @return
    *   For synchronous backends (when the effect type is [[Identity]]), [[Response]] is returned directly and
    *   exceptions are thrown. For asynchronous backends (when the effect type is e.g. [[scala.concurrent.Future]]), an
    *   effect containing the [[Response]] is returned. Exceptions are represented as failed effects (e.g. failed
    *   futures).
    *
    * The response body is deserialized as specified by this request (see [[RequestT.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send[F[_], P](backend: SttpBackend[F, P])(implicit
      isIdInRequest: IsIdInRequest[U],
      pEffectFIsR: P with Effect[F] <:< R
  ): F[Response[T]] = backend.send(this.asInstanceOf[Request[T, P with Effect[F]]]) // as witnessed by pEffectFIsR

  // we need to provide a specialised version of send with Identity so that overloads with SttpClient work correctly, e.g. in:
  // val response: Response[...] = request.send(identityBackend)
  /** Sends the request, using the given backend. Only requests for which the method & URI are specified can be sent.
    *
    * The required capabilities must be a subset of the capabilities provided by the backend.
    *
    * @return
    *   This variant of [[send]] requires a synchronous backend. [[Response]] is returned directly and exceptions are
    *   thrown.
    *
    * The response body is deserialized as specified by this request (see [[RequestT.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send[P](backend: SttpBackend[Identity, P])(implicit
      isIdInRequest: IsIdInRequest[U],
      pEffectFIsR: P with Effect[Identity] <:< R
  ): Response[T] =
    backend.send(this.asInstanceOf[Request[T, P with Effect[Identity]]]) // as witnessed by isIdInRequest & pEffectFIsR

  def send(client: SttpClient)(implicit
      isIdInRequest: IsIdInRequest[U],
      effectIdentityIsR: Effect[Identity] <:< R
  ): Response[T] = client.backend.send(
    this.asInstanceOf[Request[T, Effect[Identity]]]
  ) // as witnessed by isIdInRequest & effectIdentityIsR

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given file.
    */
  def body(file: File): RequestT[U, T, R] = body(SttpFile.fromFile(file))

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given file.
    */
  def body(path: Path): RequestT[U, T, R] = body(SttpFile.fromPath(path))

  // this method needs to be in the extensions, so that it has lowest priority when considering overloading options
  /** If content type is not yet specified, will be set to `application/octet-stream`.
    */
  def body[B: BodySerializer](b: B): RequestT[U, T, R] =
    withBody(implicitly[BodySerializer[B]].apply(b))
}
