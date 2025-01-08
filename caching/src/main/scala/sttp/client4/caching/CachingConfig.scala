package sttp.client4.caching

import sttp.model.Method
import sttp.model.ResponseMetadata
import sttp.model.RequestMetadata
import scala.concurrent.duration.FiniteDuration
import sttp.model.headers.CacheDirective
import sttp.model.HeaderNames

/** @param eligibleForCaching
  *   Checks if a request is eligible for caching, before it is sent.
  * @param cacheDuration
  *   Calculates the duration for which a response should be cached, based on the request and response. A `None` result
  *   means that the response should not be cached.
  */
case class CachingConfig(
    eligibleForCaching: RequestMetadata => Boolean = CachingConfig.EligibleWhenMethodIsGetOrHead,
    cacheDuration: (RequestMetadata, ResponseMetadata) => Option[FiniteDuration] =
      CachingConfig.CacheDurationFromCacheDirectives
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

  /** Default caching config. Caching happens when:
    *   - the request is a GET or HEAD request
    *   - the response contains a Cache-Control header with a max-age directive; the response is cached for the duration
    *     specified in this directive
    */
  val Default: CachingConfig = CachingConfig()
}
