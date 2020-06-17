# Headers

Arbitrary headers can be set on the request using the `.header` method:

```scala
basicRequest.header("User-Agent", "myapp")
```

As with any other request definition modifier, this method will yield a new request, which has the given header set. The headers can be set at any point when defining the request, arbitrarily interleaved with other modifiers.

While most headers should be set only once on a request, HTTP allows setting a header multiple times. That's why the `header` method has an additional optional boolean parameter, `replaceExisting`, which defaults to `true`. This way, if the same header is specified twice, only the last value will be included in the request. If previous values should be preserved, set this parameter to `false`.

There are also variants of this method accepting a number of headers:

```scala
def header(h: Header, replaceExisting: Boolean = false)
def header(k: String, v: String)
def header(k: String, v: String, replaceExisting: Boolean)
def headers(hs: Map[String, String])
def headers(hs: (String, String)*)
def headers(hs: Header*)
```

## Common headers

For some common headers, dedicated methods are provided:

```scala
def contentType(ct: String)
def contentType(ct: String, encoding: String)
def contentLength(l: Long)
def acceptEncoding(encoding: String)
```    

See also documentation on setting [cookies](cookies.html) and [authentication](authentication.html).
