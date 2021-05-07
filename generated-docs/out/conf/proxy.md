# Proxy support

sttp library by default checks for your System proxy properties ([docs](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html)):

Following settings are checked:

1. `socksProxyHost` and `socksProxyPort` *(default: 1080)*
2. `http.proxyHost` and `http.proxyPort` *(default: 80)*
3. `https.proxyHost` and `https.proxyPort` *(default: 443)*

Settings are loaded **in given order** and the **first existing value** is being used.

Otherwise, proxy values can be specified manually when creating a backend:

```scala
import sttp.client3._

val backend = HttpURLConnectionBackend(
  options = SttpBackendOptions.httpProxy("some.host", 8080))

basicRequest
  .get(uri"...")
  .send(backend) // uses the proxy
```

Or in case your proxy requires authentication (supported by the JVM backends):

```scala
import sttp.client3._

SttpBackendOptions.httpProxy("some.host", 8080, "username", "password")
```

## Ignoring and allowing specific hosts

There are two additional settings that can be provided to via `SttpBackendOptions`:

* `nonProxyHosts`: used to define hosts for which request SHOULD NOT be proxied
* `onlyProxyHosts`: used to define hosts for which request SHOULD be proxied

If only `nonProxyHosts` is provided, then some hosts will be skipped when proxying.
If only `onlyProxyHosts` is provided, then requests will be proxied only if host matches provided list. 
If both `nonProxyHosts` and `onlyProxyHosts` are provided, then `nonProxyHosts` takes precedence. 
Both of these options are `Nil` by default.

### Wildcards

It is possible to use wildcard, but only as either prefix or suffix. E.g. `onlyProxyHosts = List("localhost", "*.local", "127.*")`
