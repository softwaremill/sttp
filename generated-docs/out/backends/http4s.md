# Http4s backend

This backend is based on [http4s](https://http4s.org) (client) and is **asynchronous**. To use, add the following dependency to your project:

```scala
// for cats-effect 3.x & http4s 1.0.0-Mx
"com.softwaremill.sttp.client4" %% "http4s-backend" % "4.0.25" 
// or for cats-effect 2.x & http4s 0.21.x
"com.softwaremill.sttp.client4" %% "http4s-ce2-backend" % "4.0.25"
```

The backend can be created in a couple of ways, e.g.:

```scala
import cats.effect.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.client4.http4s.*

// the "org.http4s" %% "http4s-ember-client" % http4sVersion dependency needs to be explicitly added
Http4sBackend.usingDefaultEmberClientBuilder[IO](): Resource[IO, StreamBackend[IO, Fs2Streams[IO]]]

// the "org.http4s" %% "http4s-blaze-client" % http4sVersion dependency needs to be explicitly added
Http4sBackend.usingDefaultBlazeClientBuilder[IO](): Resource[IO, StreamBackend[IO, Fs2Streams[IO]]]
```

Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `http4s`. 

There are also [other cats-effect-based backends](catseffect.md), which don't depend on http4s. 

## Scala Native Support

When using the http4s backend on Scala Native, the `EmberClientBuilder` methods require a `Network[F]` constraint in addition to `Async[F]`:

```scala
import cats.effect.*
import fs2.io.net.Network
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.client4.http4s.*

// On Scala Native, this requires an implicit Network[IO] in scope
Http4sBackend.usingDefaultEmberClientBuilder[IO](): Resource[IO, StreamBackend[IO, Fs2Streams[IO]]]
```

The `Network[IO]` instance is provided automatically when extending `cats.effect.IOApp` or can be summoned explicitly using `Network[IO]` where needed. This constraint is specific to Scala Native; on the JVM, only the `Async[F]` constraint is required.

Note that the Blaze client is JVM-only and not available on Scala Native.

## Notes

* the backend contains **optional** dependencies on `http4s-ember-client` and `http4s-blaze-client`, to provide the `Http4sBackend.usingEmberClientBuilder`, `Http4sBackend.usingBlazeClientBuilder`, `Http4sBackend.usingDefaultEmberClientBuilder` and `Http4sBackend.usingDefaultBlazeClientBuilder` methods. This makes the client usable with other http4s client implementations, without the need to depend on ember or blaze.
* the backend does not support `SttpBackendOptions`, that is specifying proxy settings (proxies are not implemented in http4s, see [this issue](https://github.com/http4s/http4s/issues/251)), as well as configuring the connect timeout 
* the backend does not support the `RequestT.options.readTimeout` option

Instead, all custom timeout configuration should be done by creating a `org.http4s.client.Client[F]`, using e.g. `org.http4s.client.blaze.BlazeClientBuilder[F]` and passing it to the appropriate method of the `Http4sBackend` object.

The backend supports streaming using fs2. For usage details, see the documentation on [streaming using fs2](fs2.md).

The backend doesn't support [websockets](../other/websockets.md).
