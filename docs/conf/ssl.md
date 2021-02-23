# SSL

SSL handling can be customized (or disabled) when creating a backend and is backend-specific.

Depending on the underlying backend's client, you can customize SSL settings.

## SSL Context

Common requirement for handling SSL is creating `SSLContext`. It's required by several backends.

### One way SSL

Example assumes that you have your client key store in `.p12` format. If you have your credentials in `.pem` format covert them using:

`openssl pkcs12 -export -inkey your_key.pem -in your_cert.pem -out your_cert.p12`

Sample code might look like this:
```scala mdoc:compile-only
import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import java.security.cert.X509Certificate
import javax.net.ssl._

val TrustAllCerts: X509TrustManager = new X509TrustManager() {
  def getAcceptedIssuers: Array[X509Certificate] = Array[X509Certificate]()
  override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
  override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
}

val ks: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType)
ks.load(new FileInputStream("/path/to/your_cert.p12"), "pass".toCharArray)

val kmf: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
kmf.init(ks, "pass".toCharArray)

val ssl: SSLContext = SSLContext.getInstance("TLS")
ssl.init(kmf.getKeyManagers, Array(TrustAllCerts), new SecureRandom)
```

### Mutual SSL

In mutual SSL you are also validating server certificate so example assumes you have it in your trust store.
It can be imported to trust store with:

`keytool -import -alias server_alias -file server.cer -keystore server_trust`

Next, based on [one way SSL example](#one-way-ssl), add `TrustManagerFactory` to your code:

```scala mdoc:invisible
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl._
import java.io.FileInputStream
def ks: KeyStore = ???
def ssl: SSLContext = ???
def kmf: KeyManagerFactory = ???
```

```scala mdoc:compile-only
ks.load(new FileInputStream("/path/to/server_trust"), "pass".toCharArray)

val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm) 
tmf.init(ks)

val ssl: SSLContext = SSLContext.getInstance("TLS")
ssl.init(kmf.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
```

## Using HttpUrlConnection

Using `SSLContext` from [first section](#ssl-context) define a function to customize connection.

```scala mdoc:compile-only
import sttp.client3._
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

def useSSL(conn: HttpURLConnection): Unit =
  conn match {
    case https: HttpsURLConnection => https.setSSLSocketFactory(ssl.getSocketFactory)
    case _ => ()
  }

val backend = HttpURLConnectionBackend(customizeConnection = useSSL)
```

It is also possible to set default `SSLContext` using `SSLContext.setDefault(ssl)`.

## Using Akka-http

Using `SSLContext` from [first section](#ssl-context) create a `HttpsConnectionContext`.

```scala mdoc:compile-only
import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import sttp.client3.akkahttp._

val actorSystem: ActorSystem = ActorSystem()
val https: HttpsConnectionContext = ConnectionContext.httpsClient(ssl)

val backend = AkkaHttpBackend.usingActorSystem(actorSystem, customHttpsContext = Some(https))
```

For more information refer to [akka docs](https://doc.akka.io/docs/akka-http/current/client-side/client-https-support.html).

## Using Async-http-client

Using `kmf: KeyManagerFactory` and `tmf: TrustManagerFactory` from [first section](#ssl-context) create a `AsyncHttpClientConfig`.

Backends using `AsyncHttpClient` provides factory methods accepting custom config.

```scala mdoc:invisible
def tmf: TrustManagerFactory = ???
```

```scala mdoc:compile-only
import io.netty.handler.ssl.SslContextBuilder
import org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import sttp.client3.asynchttpclient.future._

val sslContext = SslContextBuilder.forClient()
  .keyManager(kmf)
  .trustManager(tmf)
  .build()

val config: AsyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder().setSslContext(sslContext).build()

val backend = AsyncHttpClientFutureBackend.usingConfig(config)
```

## Using OkHttp

Using `SSLContext` from [first section](#ssl-context) create a `OkHttpClient`. 

Specifying `X509TrustManager` explicitly is required for OkHttp. 
You can instantiate one your self, or extract one from `tmf: TrustManagerFactory` from [first section](#ssl-context).

```scala mdoc:compile-only
import okhttp3.OkHttpClient
import sttp.client3.okhttp.OkHttpFutureBackend
import javax.net.ssl.X509TrustManager

val yourTrustManager: X509TrustManager = ???

val client: OkHttpClient = new OkHttpClient.Builder()
  .sslSocketFactory(ssl.getSocketFactory, yourTrustManager)
  .build()

val backend = OkHttpFutureBackend.usingClient(client)
```

For more information refer to [okhttp docs](https://square.github.io/okhttp/https/).