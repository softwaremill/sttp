# Http4s backend

This backend is based on [http4s](https://http4s.org) (blaze client) and is **asynchronous**. To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "http4s-backend" % "3.0.0-RC11"
```

Add some imports as well:

```scala
import cats.effect._
import sttp.client3.http4s._
import scala.concurrent._

// an implicit `cats.effect.ContextShift` is required to create an instance of `cats.effect.Concurrent` 
// for `cats.effect.IO`,  as well as a `cats.effect.Blocker` instance. 
// Note that you'll probably want to use a different thread pool for blocking.
implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
val blocker: cats.effect.Blocker = Blocker.liftExecutionContext(ExecutionContext.global)
```

The backend can be created for any type implementing the `cats.effect.ConcurrentEffect` typeclass, such as `cats.effect.IO`. Moreover, an implicit `ContextShift` will have to be in scope as well.

If a blocker instance is not available, a new one can be created, and the resource definition can be chained, e.g. as follows:

```scala
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global) // or another instance
Blocker[IO].flatMap(Http4sBackend.usingDefaultClientBuilder[IO](_)).use { implicit backend => ... }
```

Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `http4s`. 

There are also [other cats-effect-based backends](catseffect.md), which don't depend on http4s. 

Please note that: 

* the backend does not support `SttpBackendOptions`,that is specifying proxy settings (proxies are not implemented in http4s, see [this issue](https://github.com/http4s/http4s/issues/251)), as well as configuring the connect timeout 
* the backend does not support the `RequestT.options.readTimeout` option

Instead, all custom timeout configuration should be done by creating a `org.http4s.client.Client[F]`, using `org.http4s.client.blaze.BlazeClientBuilder[F]` and passing it to the appropriate method of the `Http4sBackend` object.

The backend supports streaming using fs2. For usage details, see the documentation on [streaming using fs2](fs2.md#streaming).

The backend doesn't support [websockets](../websockets.md).
