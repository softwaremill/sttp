package sttp.client4.caching

import com.github.plokhotnyuk.jsoniter_scala.core._
import org.slf4j.LoggerFactory
import sttp.capabilities.Effect
import sttp.client4.*
import sttp.client4.wrappers.DelegateBackend
import sttp.model.HeaderNames
import sttp.model.Method
import sttp.model.ResponseMetadata
import sttp.model.headers.CacheDirective
import sttp.shared.Identity

import java.io.ByteArrayInputStream
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/** A backend wrapper which implements caching of HTTP responses.
  *
  * Caching happens if:
  *   - the request is a GET or HEAD request
  *   - the response-as description is "cache-friendly". This excludes non-blocking streaming responses, file-based
  *     responses and WebSockets
  *   - the response contains a Cache-Control header with a max-age directive; the response is cached for the duration
  *     specified in this directive
  *
  * The cache key is created using the request method, URI, and the values of headers specified in the `Vary` header.
  *
  * For requests which might be cached, the response's body is read into a byte array. If the cache-control header
  * specifies that the response should be cached, it is serialized to JSON and stored in the cache.
  *
  * The cache will be closed (using [[Cache.close]]) when this backend is closed.
  *
  * @param delegate
  * @param cache
  */
class CachingBackend[F[_], P](delegate: GenericBackend[F, P], cache: Cache[F]) extends DelegateBackend(delegate) {
  private val log = LoggerFactory.getLogger(this.getClass())

  import sttp.monad.syntax._
  import CachedResponse.cachedResponseCodec

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    val methodCacheable = request.method.is(Method.GET) || request.method.is(Method.HEAD)

    // Only requests with "cache-friendly" response-as descriptions can be cached, so that we can convert a cached
    // response (as a byte array) into the desired type. This is not possible if we're requesting a non-blocking
    // stream, storing the response to a file, or opening a web socket. These can only be handled by the backend
    // directly.
    val cacheable = responseAsCacheFriendly(request.response.delegate) && methodCacheable

    if (cacheable) {
      // checking if the request is already cached
      val key = cacheKey(request)
      cache.get(key).flatMap { cached =>
        cached.map(c => Try(readFromArray[CachedResponse](c))) match {
          case None => sendNotInCache(request, key)
          case Some(Success(cachedResponse)) =>
            log.debug(s"Found a cached response for ${request.showBasic}.")
            monad.unit(adjustResponseReadFromCache(cachedResponse.toResponse, request))
          case Some(Failure(e)) =>
            log.warn(s"Exception when deserializing response from cache for: ${request.showBasic}", e)
            // clear the cache & send the request
            cache.delete(key).flatMap(_ => sendNotInCache(request, key))
        }
      }
    } else {
      log.debug(s"Request ${request.showBasic} is not cacheable.")
      delegate.send(request) // we know that we won't be able to cache the response
    }
  }

  override def close(): F[Unit] = super.close().ensure(cache.close())

  private def sendNotInCache[T](request: GenericRequest[T, P with Effect[F]], key: Array[Byte]): F[Response[T]] = {
    // Replacing the original response as with a byte array; we know that response-as is cache-friendly, so we'll be
    // able to obtain a T-body later.
    val byteArrayRequest = requestWithResponseAsByteArray(request)
    delegate
      .send(byteArrayRequest)
      .flatMap { byteArrayResponse =>
        cacheDuration(byteArrayResponse) match {
          case Some(d) =>
            log.debug(s"Storing response for ${request.showBasic} in the cache.")
            cache
              .set(key, writeToArray(CachedResponse(byteArrayResponse)), d)
              .map(_ => byteArrayResponse)
          case None => monad.unit(byteArrayResponse)
        }
      }
      .map { byteArrayResponse =>
        adjustResponseReadFromCache(byteArrayResponse, request)
      }
  }

  private def adjustResponseReadFromCache[T](
      responseFromCache: Response[Array[Byte]],
      request: GenericRequest[T, _]
  ): Response[T] = {
    // We assume that it has been verified that responseAs is cache-friendly, so this won't throw an UOE.
    val body: T = runResponseAs(request.response.delegate, responseFromCache.body, responseFromCache)
    responseFromCache.copy(body = body)
  }

  private def cacheKey(request: GenericRequest[_, _]): Array[Byte] = {
    val base = s"${request.method} ${request.uri}"
    // the list of headers to include in the cache key, basing on the Vary header
    val varyHeaders: List[String] = request
      .header(HeaderNames.Vary)
      .map(_.split(",").toList.map(_.trim))
      .getOrElse(Nil)

    varyHeaders
      .foldLeft(base)((key, headerName) => key + s" ${headerName}=${request.header(headerName)}")
      .getBytes()
  }

  private def responseAsCacheFriendly(responseAs: GenericResponseAs[_, _]): Boolean =
    responseAs match {
      case IgnoreResponse                  => true
      case ResponseAsByteArray             => true
      case ResponseAsStream(_, _)          => false
      case ResponseAsStreamUnsafe(_)       => false
      case ResponseAsInputStream(_)        => true
      case ResponseAsInputStreamUnsafe     => true
      case ResponseAsFile(_)               => false
      case ResponseAsWebSocket(_)          => false
      case ResponseAsWebSocketUnsafe()     => false
      case ResponseAsWebSocketStream(_, _) => false
      case ResponseAsFromMetadata(conditions, default) =>
        conditions.forall(c => responseAsCacheFriendly(c.responseAs)) && responseAsCacheFriendly(default)
      case MappedResponseAs(raw, _, _) => responseAsCacheFriendly(raw)
      case ResponseAsBoth(l, r)        => responseAsCacheFriendly(l) && responseAsCacheFriendly(r)
    }

  private def runResponseAs[T](
      responseAs: GenericResponseAs[T, _],
      data: Array[Byte],
      responseMetadata: ResponseMetadata
  ): T =
    responseAs match {
      case IgnoreResponse                  => ()
      case ResponseAsByteArray             => data
      case ResponseAsStream(_, _)          => throw new UnsupportedOperationException()
      case ResponseAsStreamUnsafe(s)       => throw new UnsupportedOperationException()
      case ResponseAsInputStream(f)        => f(new ByteArrayInputStream(data))
      case ResponseAsInputStreamUnsafe     => new ByteArrayInputStream(data)
      case ResponseAsFile(_)               => throw new UnsupportedOperationException()
      case ResponseAsWebSocket(_)          => throw new UnsupportedOperationException()
      case ResponseAsWebSocketUnsafe()     => throw new UnsupportedOperationException()
      case ResponseAsWebSocketStream(_, _) => throw new UnsupportedOperationException()
      case ResponseAsFromMetadata(conditions, default) =>
        runResponseAs(
          conditions.find(_.condition(responseMetadata)).map(_.responseAs).getOrElse(default),
          data,
          responseMetadata
        )
      case MappedResponseAs(raw, g, _) => g(runResponseAs(raw, data, responseMetadata), responseMetadata)
      case ResponseAsBoth(l, r) =>
        (runResponseAs(l, data, responseMetadata), Some(runResponseAs(r, data, responseMetadata)))
    }

  private def requestWithResponseAsByteArray[T](
      request: GenericRequest[T, P with Effect[F]]
  ): GenericRequest[Array[Byte], P with Effect[F]] =
    request match {
      case r: Request[T] @unchecked => r.response(asByteArrayAlways)
      case _ => throw new IllegalStateException("WebSocket/streaming requests are not cacheable!")
    }

  private def cacheDuration(response: Response[_]): Option[FiniteDuration] = {
    val directives: List[CacheDirective] =
      response.header(HeaderNames.CacheControl).map(CacheDirective.parse).getOrElse(Nil).flatMap(_.toOption)

    directives.collectFirst { case CacheDirective.MaxAge(d) =>
      d
    }
  }
}

object CachingBackend {
  def apply(backend: SyncBackend, cache: Cache[Identity]): SyncBackend =
    new CachingBackend(backend, cache) with SyncBackend {}

  def apply[F[_]](backend: Backend[F], cache: Cache[F]): Backend[F] =
    new CachingBackend(backend, cache) with Backend[F] {}

  def apply(backend: WebSocketSyncBackend, cache: Cache[Identity]): WebSocketSyncBackend =
    new CachingBackend(backend, cache) with WebSocketSyncBackend {}

  def apply[F[_]](backend: WebSocketBackend[F], cache: Cache[F]): WebSocketBackend[F] =
    new CachingBackend(backend, cache) with WebSocketBackend[F] {}

  def apply[F[_], S](backend: StreamBackend[F, S], cache: Cache[F]): StreamBackend[F, S] =
    new CachingBackend(backend, cache) with StreamBackend[F, S] {}

  def apply[F[_], S](backend: WebSocketStreamBackend[F, S], cache: Cache[F]): WebSocketStreamBackend[F, S] =
    new CachingBackend(backend, cache) with WebSocketStreamBackend[F, S] {}
}
