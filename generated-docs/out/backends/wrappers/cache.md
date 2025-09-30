# Caching backend

To use the caching backend, add the following dependency:

```
"com.softwaremill.sttp.client4" %% "caching-backend" % "4.0.12"
```

The backend caches responses to eligible requests, and returns them from the cache if a repeated request is made. A prerequisite for a request to be considered for caching is that its response-as description is "cache-friendly"; this excludes non-blocking streaming responses, file-based responses and WebSockets.

An implementation of a `Cache` trait is required when creating the backend. The `Cache` allows storing cached values (with a TTL), and retrieving them.

The cache is highly configurable, including:
* determining if a request is eligible for caching (before it is sent)
* computing the cache key
* computing the caching duration (basing on the response)
* serialization and deserialization of the response

To use, wrap your backend (the below uses default configuration):

```scala
import sttp.client4.caching.CachingBackend

CachingBackend(delegateBackend, myCacheImplementation)
```

## Default configuration

Using `CachingConfig.Default`, caching happens if:

* the request is a `GET` or `HEAD` request 
* the response contains a `Cache-Control` header with a `max-age` directive (standard HTTP semantics); the response is cached for the duration specified in this directive

The cache key is created using the request method, URI, and the values of headers specified in the `Vary` header.

For requests which might be cached, the response's body is read into a byte array. If the response is determined to be cached, it is serialized to JSON (using jsoniter-scala) and stored in the cache.

See [examples](../../examples.md) for an example usage of the caching backend, using Redis.
