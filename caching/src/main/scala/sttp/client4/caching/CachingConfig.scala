package sttp.client4.caching

import sttp.model.Method

case class CachingConfig(
    attemptCachingForMethods: Set[Method] = Set(Method.GET, Method.HEAD)
)

object CachingConfig {
  val Default: CachingConfig = CachingConfig()
}
