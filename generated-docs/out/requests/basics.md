# Request definition basics

As mentioned in the [quickstart](../quickstart.md), the following import will be needed:

```scala
import sttp.client4.*
```

This brings into scope `basicRequest`, the starting request. This request can be customised, each time yielding a new, immutable request definition (unless a mutable body is set on the request, such as a byte array). As the request definition is immutable, it can be freely stored in values, shared across threads, and customized multiple times in various ways.

For example, we can set a cookie, `String` -body and specify that this should be a `POST` request to a given URI:

```scala
val request = basicRequest
  .cookie("login", "me")
  .body("This is a test")
  .post(uri"http://endpoint.com/secret")
```

The request parameters (headers, cookies, body etc.) can be specified **in any order**. It doesn't matter if the request method, the body, the headers or connection options are specified in this sequence or another. This way you can build arbitrary request templates, capturing all that's common among your requests, and customizing as needed. Remember that each time a modifier is applied to a request, you get a new immutable object.

There's a lot of ways in which you can customize a request, which are covered in this guide. Another option is to just explore the API: most of the methods are self-explanatory and carry scaladocs if needed.

Using the modifiers, each time we get a new request definition, but it's just a description: a data object; nothing is sent over the network until the `send(backend)` method is invoked.

## Query parameters and URIs

Query parameters are specified as part of the URI, to which the request should be sent. The URI can only be set together with the request method (using `.get(Uri)`, `.post(Uri)`, etc.).

The URI can be created programmatically (by calling methods on the `Uri` class), or using the `uri` interpolator, which also allows embedding (and later escaping) values from the environment. See the documentation on [creating URIs](../model/uri.md) for more details.

## Sending a request

A request definition can be created without knowing how it will be sent. But to send a request, a backend is needed. A default, synchronous backend based on Java's `HttpClient` is provided in the `core` jar.

To invoke the `send(backend)` method on a request description, you'll need an instance of `Backend`:

```scala
val backend = DefaultSyncBackend()
val response: Response[Either[String, String]] = request.send(backend)
```        

The default backend invokes any effects synchronously. Other, asynchronous backends, use "wrapper" effect types, such as `Future` or `IO`. See the section on [backends](../backends/summary.md) for more details.

```{note}
Only requests with the request method and uri can be sent. When trying to send a request without these components specified, a compile-time error will be reported. On how this is implemented, see the documentation on the [type of request definitions](type.md).
```

## Initial requests

sttp provides three initial requests:

* `basicRequest`, which is an empty request with the `Accept-Encoding: gzip, deflate` header added. That's the one that is most commonly used. By default reads the response as a `Either[String, String]` (indicating HTTP 4xx/5xx failure or 2xx success).
* `emptyRequest`, a completely empty request, with no headers at all.
* `quickRequest`, which always reads the response as a `String`, regardless of the status code

How the response body is handled can be (and very often is) customized. See the section on [response body specifications](../responses/body.md) for more details.
