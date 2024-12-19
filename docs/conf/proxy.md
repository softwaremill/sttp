# Proxy support

sttp library by default checks for your System proxy properties ([docs](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html)):

Following settings are checked:

1. `socksProxyHost` and `socksProxyPort` *(default: 1080)*
2. `http.proxyHost` and `http.proxyPort` *(default: 80)*
3. `https.proxyHost` and `https.proxyPort` *(default: 443)*

Settings are loaded **in given order** and the **first existing value** is being used.

Otherwise, proxy values can be specified manually when creating a backend:

```scala mdoc:compile-only
import sttp.client4.*

val backend = DefaultSyncBackend(
  options = BackendOptions.httpProxy("some.host", 8080))

basicRequest
  .get(uri"...")
  .send(backend) // uses the proxy
```

Or in case your proxy requires authentication (supported by the JVM backends):

```scala mdoc:compile-only
import sttp.client4.*

BackendOptions.httpProxy("some.host", 8080, "username", "password")
```

if you use a HttpClient-based backend (e.g. `HttpClientBackend`) and your proxy server requires Basic authentication, you will need to enable it by
removing Basic from the `jdk.http.auth.tunneling.disabledSchemes` networking property, or by setting a system property of the same name to "".

```scala mdoc:compile-only 
System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
```

Please be aware that enabling Basic authentication for HTTP tunneling can expose your credentials to interception, so it
should only be done if you understand the risks and your network is secure. This behaviour is described in https://www.oracle.com/java/technologies/javase/8u111-relnotes.html.

## Ignoring and allowing specific hosts

There are two additional settings that can be provided to via `BackendOptions`:

* `nonProxyHosts`: used to define hosts for which request SHOULD NOT be proxied
* `onlyProxyHosts`: used to define hosts for which request SHOULD be proxied

If only `nonProxyHosts` is provided, then some hosts will be skipped when proxying.
If only `onlyProxyHosts` is provided, then requests will be proxied only if host matches provided list. 
If both `nonProxyHosts` and `onlyProxyHosts` are provided, then `nonProxyHosts` takes precedence. 
Both of these options are `Nil` by default.

### Wildcards

It is possible to use wildcard, but only as either prefix or suffix. E.g. `onlyProxyHosts = List("localhost", "*.local", "127.*")`
