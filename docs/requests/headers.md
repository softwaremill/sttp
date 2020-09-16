# Headers

Arbitrary headers can be set on the request using the `.header` method:

```scala mdoc:compile-only
import sttp.client3._

basicRequest.header("User-Agent", "myapp")
```

As with any other request definition modifier, this method will yield a new request, which has the given header set. The headers can be set at any point when defining the request, arbitrarily interleaved with other modifiers.

While most headers should be set only once on a request, HTTP allows setting a header multiple times. That's why the `header` method has an additional optional boolean parameter, `replaceExisting`, which defaults to `true`. This way, if the same header is specified twice, only the last value will be included in the request. If previous values should be preserved, set this parameter to `false`.

There are also variants of this method accepting a number of headers:

```scala mdoc:compile-only
import sttp.client3._
import sttp.model._

basicRequest.header(Header("k1", "v1"), replaceExisting = false)
basicRequest.header("k2", "v2")
basicRequest.header("k3", "v3", replaceExisting = true)
basicRequest.headers(Map("k4" -> "v4", "k5" -> "v5"))
basicRequest.headers(Header("k9", "v9"), Header("k10", "v10"), Header("k11", "v11"))
```

## Common headers

For some common headers, dedicated methods are provided:

```scala mdoc:compile-only
import sttp.client3._

basicRequest.contentType("application/json")
basicRequest.contentType("application/json", "iso-8859-1")
basicRequest.contentLength(128)
basicRequest.acceptEncoding("gzip, deflate")
```    

See also documentation on setting [cookies](cookies.md) and [authentication](authentication.md).
