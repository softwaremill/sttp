# Responses

Responses are represented as instances of the case class `Response[T]`, where `T` is the type of the response body. When sending a request, the response will be returned in a wrapper. For example, for asynchronous backends, we can get a `Future[Response[T]]`, while for the default synchronous backend, the wrapper will be a no-op, `Id`, which is the same as no wrapper at all.

If sending the request fails, either due to client or connection errors, an exception will be thrown (synchronous backends), or an error will be represented in the wrapper (e.g. a failed future).

```eval_rst
.. note:: If the request completes, but results in a non-2xx return code, the request is still considered successful, that is, a ``Response[T]`` will be returned. See :doc:`response body specifications <body>` for details on how such cases are handled.
```

## Response code

The response code is available through the `.code` property. There are also methods such as `.isSuccess` or `.isServerError` for checking specific response code ranges.

## Response headers

Response headers are available through the `.headers` property, which gives all headers as a sequence (not as a map, as there can be multiple headers with the same name).

Individual headers can be obtained using the methods:

```scala
import sttp.model._
import sttp.client._
implicit val backend = HttpURLConnectionBackend()
val request = basicRequest
    .get(uri"https://httpbin.org/get")
val response = request.send()

val singleHeader: Option[String] = response.header(HeaderNames.Server)
val multipleHeaders: Seq[String] = response.headers(HeaderNames.Allow)
```

There are also helper methods available to read some commonly accessed headers:

```scala
val contentType: Option[String] = response.contentType
val contentLength: Option[Long] = response.contentLength
```

Finally, it's possible to parse the response cookies into a sequence of the `CookieWithMeta` case class:

```scala
import sttp.model._

val cookies: Seq[CookieWithMeta] = response.cookies
```        

If the cookies from a response should be set without changes on the request, this can be done directly; see the [cookies](../requests/cookies.md) section in the request definition documentation.

## Obtaining the response body

The response body can be obtained through the `.body: T` property. `T` is the body deserialized as specified in the request description - see
the next section on [response body specifications](body.md).
