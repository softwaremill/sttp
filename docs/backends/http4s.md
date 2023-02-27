# Http4s backend

This backend is based on [http4s](https://http4s.org) (client) and is **asynchronous**. To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "http4s-backend" % "@VERSION@" // for cats-effect 3.x & http4s 1.0.0-Mx
// or
"com.softwaremill.sttp.client3" %% "http4s-ce2-backend" % "@VERSION@" // for cats-effect 2.x & http4s 0.21.x
```

Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `http4s`. 

There are also [other cats-effect-based backends](catseffect.md), which don't depend on http4s. 

Please note that: 

* you have to build up your own `Client[F]` by either using _Blaze_, _Ember_ or something else
* the backend does not support `SttpBackendOptions`, that is specifying proxy settings (proxies are not implemented in http4s, see [this issue](https://github.com/http4s/http4s/issues/251)), as well as configuring the connect timeout 
* the backend does not support the `RequestT.options.readTimeout` option

Instead, all custom timeout configuration should be done by creating a `org.http4s.client.Client[F]`, using e.g. `org.http4s.client.blaze.BlazeClientBuilder[F]` and passing it to the appropriate method of the `Http4sBackend` object.

The backend supports streaming using fs2. For usage details, see the documentation on [streaming using fs2](fs2.md#streaming).

The backend doesn't support [websockets](../websockets.md).
