# Proxy support

sttp library by default checks for your System proxy properties ([docs](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html)):

Following settings are checked:

1. `socksProxyHost` and `socksProxyPort` *(default: 1080)*
2. `http.proxyHost` and `http.proxyPort` *(default: 80)*
3. `https.proxyHost` and `https.proxyPort` *(default: 443)*

Settings are loaded **in given order** and the **first existing value** is being used.

Otherwise, proxy values can be specified manually when creating a backend:

```scala
import sttp.client._

implicit val backend = HttpURLConnectionBackend(
  options = SttpBackendOptions.httpProxy("some.host", 8080))

basicRequest
  .get(uri"...")
  .send() // uses the proxy
```

Or in case your proxy requires authentication (supported by the JVM backends):

```scala
import sttp.client._

SttpBackendOptions.httpProxy("some.host", 8080, "username", "password")
```
