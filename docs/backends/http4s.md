# Http4s backend

This backend is based on [http4s](https://http4s.org) (blaze client) and is **asynchronous**. To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "http4s-backend" % "2.0.6"
```

Next you'll need to add an implicit value:

```scala
import sttp.client.http4s._

implicit val sttpBackend = Http4sBackend.usingClient(client)
// or
implicit val sttpBackend = Http4sBackend.usingClientBuilder(blazeClientBuilder)
// or
implicit val sttpBackend = Http4sBackend.usingDefaultClientBuilder()
```

The backend can be created for any type implementing the `cats.effect.Effect` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `http4s`. 

There are also [other cats-effect-based backends](catseffect.html), which don't depend on http4s. 

Please note that: 

* the backend does not support `SttpBackendOptions`,that is specifying proxy settings (proxies are not implemented in http4s, see [this issue](https://github.com/http4s/http4s/issues/251)), as well as configuring the connect timeout 
* the backend does not support the `RequestT.options.readTimeout` option

Instead, all custom timeout configuration should be done by creating a `org.http4s.client.Client[F]`, using `org.http4s.client.blaze.BlazeClientBuilder[F]` and passing it to the appropriate method of the `Http4sBackend` object.

The backend supports streaming using fs2. For usage details, see the documentation on [streaming using fs2](fs2.html#streaming).

The backend doesn't support websockets.