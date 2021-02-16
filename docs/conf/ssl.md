# SSL

SSL handling can be customized (or disabled) when creating a backend and is backend-specific.

Depending on the underlying backend's client, you can customize SSL settings.

## Using `HttpUrlConnection`

This applies to built in backends: `HttpUrlConnectionBackend` and `TryHttpUrlConnectionBackend`.

Example assumes that you have your client key store in `.p12` format and optionally a server certificate imported to your trust store.
If you have your credentials in `.pem` format covert them using:

`openssl pkcs12 -export -inkey your_key.pem -in your_cert.pem -out your_cert.p12`

Server certificate can be imported to trust store with:

`keytool -import -alias server_alias -file server.cer -keystore server_trust`

To configure SSL you need custom `SSLContext`. Add required imports:
```scala
import sttp.client3._
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl._
```

Initialize `KeyManagerFactory`:
```scala
val ks: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType)
ks.load(new FileInputStream("/path/to/your_cert.p12"), "pass".toCharArray)

val kmf: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
kmf.init(ks, "pass".toCharArray)
```

If you're using mutual SSL initialize `TrustManagerFactory`:
```scala
ks.load(new FileInputStream("/path/to/server_trust"), "pass".toCharArray)

val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
tmf.init(ks)
```

Otherwise, if you are only authenticating client side create "trust all" manager:
```scala
import java.security.cert.X509Certificate

val TrustAll: X509TrustManager = new X509TrustManager() {
  def getAcceptedIssuers: Array[X509Certificate] = Array[X509Certificate]()
  override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
  override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
}
```

Next, create `SSLSocketFactory`:
```scala
val ssl: SSLContext = SSLContext.getInstance("TLS")
ssl.init(kmf.getKeyManagers, tmf.getTrustManagers, new SecureRandom) // or TrustAll.getTrustManagers    
```

Finally, define a function to customize connection using `SSLContext` from previous step.
You also need to implement `HostnameVerifier`, the simplest one is used here, accepting all hosts.
```scala
val hostnameVerifier: HostnameVerifier = (_: String, _: SSLSession) => true

val useSSL = (conn: HttpURLConnection) =>
  conn match {
    case https: HttpsURLConnection =>
      https.setSSLSocketFactory(ssl.getSocketFactory)
      https.setHostnameVerifier(hostnameVerifier)
    case _ => ()
  }

val backend = HttpURLConnectionBackend(customizeConnection = useSSL)
```

Please note, that this in only one of several possible ways to configure SSL with `HttpUrlConnection`.
Other options include setting system properties pointing to key/trust stores (see [docs](https://docs.oracle.com/cd/E29585_01/PlatformServices.61x/security/src/csec_ssl_jsp_start_server.html))
or setting default `SSLContext` using `SSLContext.setDefault(...)`.

## Other backends

* akka-http: when creating the backend, specify the `customHttpsContext: Option[HttpsConnectionContext]` parameter. See [akka-http docs](http://doc.akka.io/docs/akka-http/current/scala/http/server-side/server-https-support.html)
* async-http-client: create a custom client and use the `setSSLContext` method
* OkHttp: create a custom client modifying the SSL settings as described [on the wiki](https://github.com/square/okhttp/wiki/HTTPS)

