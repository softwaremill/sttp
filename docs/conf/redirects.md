# Redirects

By default, sttp follows redirects.

If you'd like to disable following redirects, use the `followRedirects` method:

```scala mdoc:compile-only
import sttp.client3._

basicRequest.followRedirects(false)
```

If a request has been redirected, the history of all followed redirects is accessible through the `response.history` list. The first response (oldest) comes first. The body of each response will be a `Left(message)` (as the status code is non-2xx), where the message is whatever the server returned as the response body.

## Redirecting POST requests

If a `POST` or `PUT` request is redirected, by default it will be sent unchanged to the new address, that is using the original body and method. However, most browsers and some clients issue a `GET` request in such case, without the body.

To enable this behavior, use the `redirectToGet` method:

```scala mdoc:compile-only
import sttp.client3._

basicRequest.redirectToGet(true)
```

Note that this only affects `301 Moved Permanently` and `302 Found` redirects. `303 See Other` redirects are always converted, while `307 Temporary Redirect` and `308 Permanent Redirect` never.

## Important Note on the `Authorization` header

Most modern http clients will, by default, strip the `Authorization` header when encountering a redirect; sttp client is no different.

You can disable the stripping of all sensitive headers using the following code:

```scala mdoc:compile-only
import sttp.client3._

val myBackend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
val backend: SttpBackend[Identity, Any]  = new FollowRedirectsBackend(
  delegate = myBackend, 
  sensitiveHeaders = Set.empty
)
```

If you just want to disable stripping of the `Authorization` header, you can do the following:

```scala mdoc:compile-only
import sttp.client3._
import sttp.model._

val myBackend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
val backend: SttpBackend[Identity, Any] = new FollowRedirectsBackend(
  delegate = myBackend, 
  sensitiveHeaders = HeaderNames.SensitiveHeaders.filterNot(_ == HeaderNames.Authorization.toLowerCase)
)
```

## Backend wrappers and redirects

By default redirects are handled at a low level, using a wrapper around the main, concrete backend: each of the backend factory methods, e.g. `HttpClientSyncBackend()` returns a backend wrapped in `FollowRedirectsBackend`.

This causes any further backend wrappers to handle a request which involves redirects as one whole, without the intermediate requests. However, wrappers which collects metrics, implements tracing or handles request retries might want to handle every request in the redirect chain. This can be achieved by layering another `FollowRedirectsBackend` on top of the wrapper. Only the top-level follow redirects backend will handle redirects, other follow redirect wrappers (at lower levels) will be disabled.

For example:

```scala mdoc:compile-only
import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.MonadError

class MyWrapper[F[_], P] private (delegate: SttpBackend[F, P])
  extends SttpBackend[F, P] {

  def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = ???

  def close(): F[Unit] = ???

  def responseMonad: MonadError[F] = ???
}

object MyWrapper {
  def apply[F[_], P](
    delegate: SttpBackend[F, P]): SttpBackend[F, P] = {
    // disables any other FollowRedirectsBackend-s further down the delegate chain
    new FollowRedirectsBackend(new MyWrapper(delegate))
  }
}
```

### Custom URI encoding

Whenever a redirect request is about to be created, the `FollowRedirectsBackend` uses the value provided in the `Location` header. In its simplest form, a call to `uri"$location"` is being made in order to construct these `Uri`s. The `FollowRedirectsBackend` allows modification of such `Uri` by providing a custom `transformUri: Uri => Uri` function. This might be useful if, for example, some parts of the `Uri` had been initially encoded in a more strict or lenient way.

For example:

```scala mdoc:compile-only
import sttp.client3._
import sttp.model.Uri.QuerySegmentEncoding

val myBackend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
val backend: SttpBackend[Identity, Any]  = new FollowRedirectsBackend(
  delegate = myBackend,
  // encodes all special characters in the query segment, including the allowed ones
  transformUri = _.querySegmentsEncoding(QuerySegmentEncoding.All)
)
```