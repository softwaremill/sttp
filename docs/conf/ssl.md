# SSL

SSL handling can be customized (or disabled) when creating a backend and is backend-specific.

Depending on the underlying backend's client, you can customize SSL settings.

## Using `HttpUrlConnectionBackend`

To configure SSL you need custom `SSLContext`. Add required imports:
```scala mdoc
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl._
```

Assuming you have your client key store in `.p12` format initialize `KeyManagerFactory`:
```scala  mdoc:compile-only
val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
keyStore.load(new FileInputStream("/path/to/your/keystore.p12"), "pass".toCharArray)

val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
keyManagerFactory.init(keyStore, YourPass)
```

If you're using mutual SSL, assuming you have imported server certificate to the trust store, initialize `TrustManagerFactory`:
```scala mdoc:compile-only
keyStore.load(new FileInputStream("/path/to/truststore"), "pass".toCharArray)

val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
trustManagerFactory.init(keyStore)
```

Otherwise, if you are only authenticating client side create "trust all" manager:
```scala mdoc:compile-only
import java.security.cert.X509Certificate

val TrustAll = new X509TrustManager() {
    def getAcceptedIssuers: Array[X509Certificate] = Array[X509Certificate]()
    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
  }
```

Next, create `SSLSocketFactory`:
```scala mdoc:compile-only
val sslContext = SSLContext.getInstance("TLS")
sslContext.init(
    keyManagerFactory.getKeyManagers, 
    trustManagerFactory.getTrustManagers, // or TrustAll.getTrustManagers 
    new SecureRandom())
    
val sslSocketFactory = sslContext.getSocketFactory     
```

Finally, define a function to customize connection using `sslSocketFactory` from previous step.
You also need to implement `HostnameVerifier`, the simplest one is used here.
```scala mdoc:compile-only
val useSSL = (conn: HttpURLConnection) => {
    conn match {
        case https: HttpsURLConnection =>
            https.setSSLSocketFactory(sslSocketFactory)
            https.setHostnameVerifier((_: String, _: SSLSession) => true)
    case _ => ()
    }
}

val backend = HttpURLConnectionBackend(customizeConnection = useSSL)
```

Please note, that this in only one of several possible ways to configure SSL with `HttpUrlConnectionBackend`.
Other options include setting system properties pointing to key/trust stores (see [docs](https://docs.oracle.com/cd/E29585_01/PlatformServices.61x/security/src/csec_ssl_jsp_start_server.html))
or setting default `SSLContext` using `SSLContext.setDefault(...)`.

* akka-http: when creating the backend, specify the `customHttpsContext: Option[HttpsConnectionContext]` parameter. See [akka-http docs](http://doc.akka.io/docs/akka-http/current/scala/http/server-side/server-https-support.html)
* async-http-client: create a custom client and use the `setSSLContext` method
* OkHttp: create a custom client modifying the SSL settings as described [on the wiki](https://github.com/square/okhttp/wiki/HTTPS)

