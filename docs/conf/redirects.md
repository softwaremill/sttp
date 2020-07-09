# Redirects

By default, sttp follows redirects.

If you'd like to disable following redirects, use the `followRedirects` method:

```scala mdoc:compile-only
import sttp.client._

basicRequest.followRedirects(false)
```

If a request has been redirected, the history of all followed redirects is accessible through the `response.history` list. The first response (oldest) comes first. The body of each response will be a `Left(message)` (as the status code is non-2xx), where the message is whatever the server returned as the response body.

## Redirecting POST requests

If a `POST` or `PUT` request is redirected, by default it will be sent unchanged to the new address, that is using the original body and method. However, most browsers and some clients issue a `GET` request in such case, without the body.

To enable this behavior, use the `redirectToGet` method:

```scala mdoc:compile-only
import sttp.client._

basicRequest.redirectToGet(true)
```

Note that this only affects `301 Moved Permanently` and `302 Found` redirects. `303 See Other` redirects are always converted, while `307 Temporary Redirect` and `308 Permanent Redirect` never.
