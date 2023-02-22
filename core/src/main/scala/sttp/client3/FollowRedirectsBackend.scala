package sttp.client3

import sttp.capabilities.Effect
import sttp.model._

abstract class FollowRedirectsBackend[F[_], P] private (
    delegate: GenericBackend[F, P],
    config: FollowRedirectsConfig
) extends DelegateBackend(delegate) {

  type R = P with Effect[F]

  override def send[T](request: AbstractRequest[T, R]): F[Response[T]] =
    sendWithCounter(request, 0)

  protected def sendWithCounter[T](request: AbstractRequest[T, R], redirects: Int): F[Response[T]] = {
    // if there are nested follow redirect backends, disabling them and handling redirects here
    val resp = delegate.send(request.followRedirects(false))
    if (request.options.followRedirects) {
      responseMonad.flatMap(resp) { (response: Response[T]) =>
        if (response.isRedirect) {
          followRedirect(request, response, redirects)
        } else {
          responseMonad.unit(response)
        }
      }
    } else {
      resp
    }
  }

  private def followRedirect[T](
      request: AbstractRequest[T, R],
      response: Response[T],
      redirects: Int
  ): F[Response[T]] = {
    response.header(HeaderNames.Location).fold(responseMonad.unit(response)) { loc =>
      if (redirects >= request.options.maxRedirects) {
        responseMonad.error(TooManyRedirectsException(request.uri, redirects))
      } else {
        followRedirect(request, response, redirects, loc)
      }
    }
  }

  private def followRedirect[T](
      request: AbstractRequest[T, R],
      response: Response[T],
      redirects: Int,
      loc: String
  ): F[Response[T]] = {
    val uri =
      if (FollowRedirectsBackend.isRelative(loc)) config.transformUri(request.uri.resolve(uri"$loc"))
      else config.transformUri(uri"$loc")

    val redirectResponse =
      ((stripSensitiveHeaders[T](_)) andThen
        (changePostPutToGet[T](_, response.code)) andThen
        (sendWithCounter(_, redirects + 1)))
        .apply(request.method(request.method, uri = uri))

    responseMonad.map(redirectResponse) { rr =>
      val responseNoBody = response.copy(body = ())
      rr.copy(history = responseNoBody :: rr.history)
    }
  }

  private def stripSensitiveHeaders[T](request: AbstractRequest[T, R]): AbstractRequest[T, R] = {
    request.withHeaders(
      request.headers.filterNot(h => config.sensitiveHeaders.contains(h.name.toLowerCase()))
    )
  }

  private def changePostPutToGet[T](r: AbstractRequest[T, R], statusCode: StatusCode): AbstractRequest[T, R] = {
    val applicable = r.method == Method.POST || r.method == Method.PUT
    val alwaysChanged = statusCode == StatusCode.SeeOther
    val neverChanged = statusCode == StatusCode.TemporaryRedirect || statusCode == StatusCode.PermanentRedirect
    if (applicable && (r.options.redirectToGet || alwaysChanged) && !neverChanged) {
      // when transforming POST or PUT into a get, content is dropped, also filter out content-related request headers
      r.method(Method.GET, r.uri)
        .withBody(NoBody)
        .withHeaders(r.headers.filterNot(header => config.contentHeaders.contains(header.name.toLowerCase())))
    } else r
  }
}

object FollowRedirectsBackend {
  def apply(delegate: SyncBackend): SyncBackend = apply(delegate, FollowRedirectsConfig.Default)
  def apply[F[_]](delegate: Backend[F]): Backend[F] = apply(delegate, FollowRedirectsConfig.Default)
  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] = apply(delegate, FollowRedirectsConfig.Default)
  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] =
    apply(delegate, FollowRedirectsConfig.Default)
  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] =
    apply(delegate, FollowRedirectsConfig.Default)
  def apply(delegate: SyncBackend, config: FollowRedirectsConfig): SyncBackend =
    new FollowRedirectsBackend(delegate, config) with SyncBackend {}
  def apply[F[_]](delegate: Backend[F], config: FollowRedirectsConfig): Backend[F] =
    new FollowRedirectsBackend(delegate, config) with Backend[F] {}
  def apply[F[_]](delegate: WebSocketBackend[F], config: FollowRedirectsConfig): WebSocketBackend[F] =
    new FollowRedirectsBackend(delegate, config) with WebSocketBackend[F] {}
  def apply[F[_], S](delegate: StreamBackend[F, S], config: FollowRedirectsConfig): StreamBackend[F, S] =
    new FollowRedirectsBackend(delegate, config) with StreamBackend[F, S] {}
  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      config: FollowRedirectsConfig
  ): WebSocketStreamBackend[F, S] =
    new FollowRedirectsBackend(delegate, config) with WebSocketStreamBackend[F, S] {}

  private[client3] val MaxRedirects = 32

  private val protocol = "^[a-z]+://.*".r

  private[client3] def isRelative(uri: String): Boolean = {
    val toCheck = uri.toLowerCase().trim
    !protocol.pattern.matcher(toCheck).matches()
  }

  /** By default, the conversion is a no-op */
  val DefaultUriTransform: Uri => Uri = (uri: Uri) => uri
}

case class TooManyRedirectsException(uri: Uri, redirects: Int) extends Exception

/** @param transformUri
  *   Defines if and how [[Uri]] s from the `Location` header should be transformed. For example, this enables changing
  *   the encoding of host, path, query and fragment segments to be more strict or relaxed.
  */
case class FollowRedirectsConfig(
    contentHeaders: Set[String] = HeaderNames.ContentHeaders,
    sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
    transformUri: Uri => Uri = FollowRedirectsBackend.DefaultUriTransform
)

object FollowRedirectsConfig {
  val Default: FollowRedirectsConfig = FollowRedirectsConfig()
}
