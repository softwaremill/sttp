# Cookies

## Cookies on requests

Cookies sent in requests are key-value pairs contained in the `Cookie` header. They can be set on a request in a couple of ways. The first is using the `.cookie(name: String, value: String)` method. This will yield a new request definition which, when sent, will contain the given cookie.

Cookies are currently only available on the JVM.

Cookies can also be set using the following methods:

```scala
import sttp.client4.*
import sttp.model.headers.CookieWithMeta

basicRequest
  .cookie("k1", "v1")
  .cookie("k2" -> "v2")
  .cookies("k3" -> "v3", "k4" -> "v4")
  .cookies(Seq(CookieWithMeta("k5", "k5"), CookieWithMeta("k6", "k6")))
```

## Cookies from responses

It is often necessary to copy cookies from a response, e.g. after a login request is sent, and a successful response with the authentication cookie received. Having an object `response: Response[_]`, cookies on a request can be copied:

```scala
import sttp.client4.*

val backend = DefaultSyncBackend()
val loginRequest = basicRequest
    .cookie("login", "me")
    .body("This is a test")
    .post(uri"http://endpoint.com")
val response = loginRequest.send(backend)

basicRequest.cookies(response)
```   

Or, it's also possible to store only the `sttp.model.CookieWithMeta` objects (a sequence of which can be obtained from a response), and set the on the request:

```scala
import sttp.client4.*

val backend = DefaultSyncBackend()
val loginRequest = basicRequest
    .cookie("login", "me")
    .body("This is a test")
    .post(uri"http://endpoint.com")
val response = loginRequest.send(backend)
val cookiesFromResponse = response.unsafeCookies

basicRequest.cookies(cookiesFromResponse)
```

## Cookies across redirects

The `Cookie` header is a sensitive header, so by default it is stripped when following a redirect; cookies set via `Set-Cookie` during a redirect chain are not carried over to subsequent requests either. To opt into a cookie jar that does this, attach a `CookieStorage` to the request. The `FollowRedirectsBackend` (applied to all backends by default) then, for each request in a redirect chain, sends the stored cookies that domain/path-match the request and collects the response's `Set-Cookie` cookies into the storage:

```scala
import sttp.client4.*
import sttp.client4.wrappers.CookieStorage

val backend = DefaultSyncBackend()
basicRequest
  .cookieStorage(CookieStorage.empty)
  .get(uri"https://endpoint.com")
  .send(backend)
```

Matching follows a subset of [RFC 6265](https://www.rfc-editor.org/rfc/rfc6265): domain, path and the `Secure` attribute. Time-based expiry is not tracked, but a `Set-Cookie` with `Max-Age` <= 0 removes a matching cookie.
