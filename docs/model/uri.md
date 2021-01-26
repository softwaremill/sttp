# URIs

A request can only be sent if the request method & URI are defined. To represent URIs, sttp comes with a `Uri` case class, which captures all the parts of an address.

To specify the request method and URI, use one of the methods on the request definition corresponding to the name of the desired HTTP method: `.post`, `.get`, `.put` etc. All of them accept a single parameter, the URI to which the request should be sent (these methods only modify the request definition; they don't send the requests).

The `Uri` class is immutable, and can be constructed by hand, but in many cases the URI interpolator will be easier to use.

## URI interpolator

Using the URI interpolator it's possible to conveniently create `Uri` instances, for example:

```scala mdoc:compile-only
import sttp.client3._
import sttp.model._

val user = "Mary Smith"
val filter = "programming languages"

val endpoint: Uri = uri"http://example.com/$user/skills?filter=$filter"

assert(endpoint.toString ==
  "http://example.com/Mary%20Smith/skills?filter=programming+languages")
```

Note the `uri` prefix before the string and the standard Scala string-embedding syntax (`$user`, `$filter`).

Any values embedded in the URI will be URL-encoded, taking into account the context (e.g., the whitespace in `user` will be %-encoded as `%20D`, while the whitespace in `filter` will be query-encoded as `+`). On the other hand, parts of the URI given as literal strings (not embedded values), are assumed to be URL-encoded and thus will be decoded when creating a `Uri` instance.

All components of the URI can be embedded from values: scheme, username/password, host, port, path, query and fragment. The embedded values won't be further parsed, except the `:` in the host part, which is commonly used to pass in both the host and port:

```scala mdoc
import sttp.client3._

// the embedded / is escaped
println(uri"http://example.org/${"a/b"}")

// the embedded / is not escaped
println(uri"http://example.org/${"a"}/${"b"}")

// the embedded : is not escaped
println(uri"http://${"example.org:8080"}")
```

Both the `Uri` class, and the interpolator can be used stand-alone, without using the rest of sttp. Conversions are available both from and to `java.net.URI`; `Uri.toString` returns the URI as a `String`.

## Optional values

The URI interpolator supports optional values for hosts (subdomains), query parameters and the fragment. If the value is `None`, the appropriate URI component will be removed. For example:

```scala mdoc:silent
val v1 = None
val v2 = Some("v2")
```

```scala mdoc
println(uri"http://example.com?p1=$v1&p2=v2")

println(uri"http://$v1.$v2.example.com")

println(uri"http://example.com#$v1")
```                  

## Maps and sequences

Maps, sequences of tuples and sequences of values can be embedded in the query part. They will be expanded into query parameters. Maps and sequences of tuples can also contain optional values, for which mappings will be removed if `None`.

For example:

```scala mdoc:silent
val ps = Map("p1" -> "v1", "p2" -> "v2")
```

```scala mdoc
println(uri"http://example.com?$ps&p3=p4")
```

Sequences in the host part will be expanded to a subdomain sequence, and sequences in the path will be expanded to path components:

```scala mdoc:silent
val params = List("a", "b", "c")
```

```scala mdoc
println(uri"http://example.com/$params")
```        

## Special cases

If a string containing the protocol is embedded *at the very beginning*, it will not be escaped, allowing to embed entire addresses as prefixes, e.g.: `uri"$endpoint/login"`, where `val endpoint = "http://example.com/api"`.

This is useful when a base URI is stored in a value, and can then be used as a base for constructing more specific URIs.

## Relative URIs

The `Uri` class can represent both relative and absolute URIs. Hence, in terms of [rfc3986](https://tools.ietf.org/html/rfc3986), it is in fact a URI reference.

Relative URIs can be created using the interpolator, same as absolute ones, e.g.:

```scala mdoc
println(uri"/api/$params")
```

When sending requests using relative URIs, the [`ResolveRelativeUrisBackend`](../backends/summary.md) backend wrapper might be useful to resolve them.

## All features combined

A fully-featured example:

```scala mdoc:silent
import sttp.client3._
val secure = true
val scheme = if (secure) "https" else "http"
val subdomains = List("sub1", "sub2")
val vx = Some("y z")
val paramMap = Map("a" -> 1, "b" -> 2)
val jumpTo = Some("section2")
```

```scala mdoc
println(uri"$scheme://$subdomains.example.com?x=$vx&$paramMap#$jumpTo")
```
