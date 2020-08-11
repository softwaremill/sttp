# Cookies

Cookies sent in requests are key-value pairs contained in the `Cookie` header. They can be set on a request in a couple of ways. The first is using the `.cookie(name: String, value: String)` method. This will yield a new request definition which, when sent, will contain the given cookie.

Cookies are currently only available on the JVM.

Cookies can also be set using the following methods:

```scala
import sttp.client._
import sttp.model._

basicRequest
  .cookie("k1", "v1")
  .cookie("k2" -> "v2")
  .cookies("k3" -> "v3", "k4" -> "v4")
  .cookies(Seq(CookieWithMeta("k5", "k5"), CookieWithMeta("k6", "k6")))
```

## Cookies from responses

It is often necessary to copy cookies from a response, e.g. after a login request is sent, and a successful response with the authentication cookie received. Having an object `response: Response[_]`, cookies on a request can be copied:

```scala
import sttp.client._

val backend = HttpURLConnectionBackend()
val loginRequest = basicRequest
    .cookie("login", "me")
    .body("This is a test")
    .post(uri"http://endpoint.com")
val response = loginRequest.send(backend)

basicRequest.cookies(response)
```   

Or, it's also possible to store only the `sttp.model.CookieWithMeta` objects (a sequence of which can be obtained from a response), and set the on the request:

```scala
import sttp.client._

val backend = HttpURLConnectionBackend()
val loginRequest = basicRequest
    .cookie("login", "me")
    .body("This is a test")
    .post(uri"http://endpoint.com")
val response = loginRequest.send(backend)
val cookiesFromResponse = response.cookies

basicRequest.cookies(cookiesFromResponse)
```
