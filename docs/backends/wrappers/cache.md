# Caching backend

To use the caching backend, add the following dependency:

```
"com.softwaremill.sttp.client4" %% "caching-backend" % "@VERSION@"
```

The caching backend caches eligible responses, and returns them from the cache if a repeated request is made, using standard HTTP semantics. The backend requires an implementation of a `Cache` traits, which allows storing cached values (with a TTL), and retrieving them.

Caching happens if:

* the request is a `GET` or `HEAD` request
* the response-as description is "cache-friendly". This excludes non-blocking streaming responses, file-based responses and WebSockets
* the response contains a `Cache-Control` header with a `max-age` directive; the response is cached for the duration specified in this directive

The cache key is created using the request method, URI, and the values of headers specified in the `Vary` header.

For requests which might be cached, the response's body is read into a byte array. If the cache-control header specifies that the response should be cached, it is serialized to JSON and stored in the cache.

See [examples](../../examples.md) for an example usage of the caching backend, using Redis.
