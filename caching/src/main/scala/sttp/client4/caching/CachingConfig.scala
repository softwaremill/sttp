package sttp.client4.caching

import com.github.plokhotnyuk.jsoniter_scala.core._
import sttp.model.HeaderNames
import sttp.model.Method
import sttp.model.RequestMetadata
import sttp.model.ResponseMetadata
import sttp.model.headers.CacheDirective

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/** Configuration for [[CachingBackend]].
  *
  * @param eligibleForCaching
  *   Checks if a request is eligible for caching, before it is sent. By default only GET and HEAD requests are
  *   eligible.
  * @param cacheDuration
  *   Calculates the duration for which a response should be cached, based on the request and response. A `None` result
  *   means that the response should not be cached. By default uses the max-age cache directive from the `Cache-Control`
  *   header.
  * @param cacheKey
  *   Creates the cache key for a request. The default implementation includes the method, URI and headers specified in
  *   the `Vary` header.
  * @param serializeResponse
  *   The function used to serialize the response to be cached. By default uses JSON serialization using jsoniter-scala.
  * @param deserializeResponse
  *   The function used to deserialize the response from the cache. By default uses JSON deserialization using
  *   jsoniter-scala.
  */
case class CachingConfig(
    eligibleForCaching: RequestMetadata => Boolean = CachingConfig.EligibleWhenMethodIsGetOrHead,
    cacheDuration: (RequestMetadata, ResponseMetadata) => Option[FiniteDuration] =
      CachingConfig.CacheDurationFromCacheDirectives,
    cacheKey: RequestMetadata => Array[Byte] = CachingConfig.DefaultCacheKey,
    serializeResponse: CachedResponse => Array[Byte] = CachingConfig.SerializeResponseToJson,
    deserializeResponse: Array[Byte] => Try[CachedResponse] = CachingConfig.DeserializeResponseFromJson
)

object CachingConfig {
  val EligibleWhenMethodIsGetOrHead: RequestMetadata => Boolean = { request =>
    request.method == Method.GET || request.method == Method.HEAD
  }

  val CacheDurationFromCacheDirectives: (RequestMetadata, ResponseMetadata) => Option[FiniteDuration] = {
    (_, response) =>
      val directives: List[CacheDirective] =
        response.header(HeaderNames.CacheControl).map(CacheDirective.parse).getOrElse(Nil).flatMap(_.toOption)

      directives.collectFirst { case CacheDirective.MaxAge(d) =>
        d
      }
  }

  val DefaultCacheKey: RequestMetadata => Array[Byte] = { request =>
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

  val SerializeResponseToJson: CachedResponse => Array[Byte] = response => writeToArray(response)
  val DeserializeResponseFromJson: Array[Byte] => Try[CachedResponse] = bytes =>
    Try(readFromArray[CachedResponse](bytes))

  /** Default caching config. Caching happens when:
    *   - the request is a GET or HEAD request
    *   - the response contains a Cache-Control header with a max-age directive; the response is cached for the duration
    *     specified in this directive
    *
    * Responses are stored in the cache, serialized to JSON using jsoniter-scala.
    */
  val Default: CachingConfig = CachingConfig()
}
