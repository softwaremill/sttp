package sttp.client4.wrappers

import sttp.capabilities.Effect
import sttp.client4._
import sttp.model._
import sttp.model.Uri.QuerySegmentEncoding
import sttp.monad.syntax._

abstract class FollowRedirectsBackend[F[_], P] private (
    delegate: GenericBackend[F, P],
    config: FollowRedirectsConfig
) extends DelegateBackend(delegate) {

  type R = P with Effect[F]

  override def send[T](request: GenericRequest[T, R]): F[Response[T]] = sendWithCounter(request, 0)

  protected def sendWithCounter[T](request: GenericRequest[T, R], redirects: Int): F[Response[T]] = {
    // if there are nested follow redirect backends, disabling them and handling redirects here
    // using a def instead of a val so that errors are properly caught
    def resp = delegate.send(request.followRedirects(false))

    if (request.options.followRedirects) {
      resp
        .flatMap { (response: Response[T]) =>
          if (response.isRedirect) {
            followRedirect(request, response, redirects)
          } else {
            monad.unit(response)
          }
        }
        .handleError { e =>
          ResponseException.find(e) match {
            case Some(re) if re.response.isRedirect =>
              re.response.header(HeaderNames.Location) match {
                case None      => monad.error(e) // no location header, propagating the exception
                case Some(loc) => followRedirect(request, re.response, redirects, loc)
              }
            case _ => monad.error(e)
          }
        }
    } else {
      resp
    }
  }

  private def followRedirect[T](
      request: GenericRequest[T, R],
      response: Response[T],
      redirects: Int
  ): F[Response[T]] =
    response.header(HeaderNames.Location) match {
      case None      => monad.unit(response)
      case Some(loc) =>
        if (redirects >= request.options.maxRedirects) {
          monad.error(new SttpClientException.TooManyRedirectsException(request, redirects))
        } else {
          followRedirect(request, response, redirects, loc)
        }
    }

  private def followRedirect[T](
      request: GenericRequest[T, R],
      response: ResponseMetadata,
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

    monad.map(redirectResponse) { rr =>
      rr.copy(history = response :: rr.history)
    }
  }

  private def stripSensitiveHeaders[T](request: GenericRequest[T, R]): GenericRequest[T, R] =
    request.withHeaders(
      request.headers.filterNot(h => config.sensitiveHeaders.contains(h.name.toLowerCase()))
    )

  private def changePostPutToGet[T](r: GenericRequest[T, R], statusCode: StatusCode): GenericRequest[T, R] = {
    val applicable = r.method == Method.POST || r.method == Method.PUT
    val alwaysChanged = statusCode == StatusCode.SeeOther
    val neverChanged = statusCode == StatusCode.TemporaryRedirect || statusCode == StatusCode.PermanentRedirect
    if (applicable && (r.options.redirectToGet || alwaysChanged) && !neverChanged) {
      // when transforming POST or PUT into a get, content is dropped, also filter out content-related request headers
      r.method(Method.GET, r.uri)
        .body(NoBody)
        .withHeaders(r.headers.filterNot(header => config.contentHeaders.contains(header.name.toLowerCase())))
    } else r
  }
}

/** A backend wrapper that follows redirects. By default applied to all backends, but can be applied on a backend again
  * to provide alternate configuration, or to follow redirects later in the response processing pipeline (e.g. after
  * metrics).
  *
  * The URIs to which requests are redirected are parsed and then serialized (plus optionally transformed as specified
  * in the configuration).
  *
  * To always encode all characters in query segments of the target URIs, even if they don't need to be encoded
  * according to the RFC, use the [[encodeUriAll]] method to wrap your backend.
  *
  * @see
  *   [[FollowRedirectsConfig]]
  */
object FollowRedirectsBackend {
  def apply(delegate: SyncBackend): SyncBackend = apply(delegate, FollowRedirectsConfig.Default)
  def apply[F[_]](delegate: Backend[F]): Backend[F] = apply(delegate, FollowRedirectsConfig.Default)
  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] = apply(delegate, FollowRedirectsConfig.Default)
  def apply(delegate: WebSocketSyncBackend): WebSocketSyncBackend = apply(delegate, FollowRedirectsConfig.Default)
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
  def apply(delegate: WebSocketSyncBackend, config: FollowRedirectsConfig): WebSocketSyncBackend =
    new FollowRedirectsBackend(delegate, config) with WebSocketSyncBackend {}
  def apply[F[_], S](delegate: StreamBackend[F, S], config: FollowRedirectsConfig): StreamBackend[F, S] =
    new FollowRedirectsBackend(delegate, config) with StreamBackend[F, S] {}
  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      config: FollowRedirectsConfig
  ): WebSocketStreamBackend[F, S] =
    new FollowRedirectsBackend(delegate, config) with WebSocketStreamBackend[F, S] {}

  def encodeUriAll(delegate: SyncBackend): SyncBackend = apply(delegate, FollowRedirectsConfig.EncodeUriAll)
  def encodeUriAll[F[_]](delegate: Backend[F]): Backend[F] = apply(delegate, FollowRedirectsConfig.EncodeUriAll)
  def encodeUriAll[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] =
    apply(delegate, FollowRedirectsConfig.EncodeUriAll)
  def encodeUriAll(delegate: WebSocketSyncBackend): WebSocketSyncBackend =
    apply(delegate, FollowRedirectsConfig.EncodeUriAll)
  def encodeUriAll[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] =
    apply(delegate, FollowRedirectsConfig.EncodeUriAll)
  def encodeUriAll[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] =
    apply(delegate, FollowRedirectsConfig.EncodeUriAll)

  private[client4] val MaxRedirects = 32

  private val protocol = "^[a-z]+://.*".r

  private[client4] def isRelative(uri: String): Boolean = {
    val toCheck = uri.toLowerCase().trim
    !protocol.pattern.matcher(toCheck).matches()
  }

  /** By default, the conversion is a no-op */
  val DefaultUriTransform: Uri => Uri = (uri: Uri) => uri
}

/** @param contentHeaders
  *   When redirecting a POST to a GET, the content is dropped. This option defines which content-related headers should
  *   be removed from the request in that case.
  * @param sensitiveHeaders
  *   The headers that are always removed from the request when following a redirect. By default includes
  *   security-related headers.
  * @param transformUri
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

  // #2505
  /** @see https://sttp.softwaremill.com/en/latest/model/uri.html#faq-encoding-decoding-uri-components */
  val EncodeUriAll: FollowRedirectsConfig =
    FollowRedirectsConfig(transformUri = _.querySegmentsEncoding(QuerySegmentEncoding.All))
}
