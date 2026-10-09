# Scala Native (curl) backend

A Scala Native (0.5.x) backend implemented using [Curl](https://github.com/curl/curl/blob/master/include/curl/curl.h).

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %%% "core" % "@VERSION@"
```

and initialize one of the backends:

```scala
import sttp.client4.curl.*

val backend = CurlBackend()
val tryBackend = CurlTryBackend()
```

You need to have an environment with Scala Native [setup](https://scala-native.readthedocs.io/en/latest/user/setup.html)
with additionally installed `libcrypto` (included in OpenSSL) and `curl` in version `7.56.0` or newer.

## scala-cli example

Try the following example:

```scala
// hello.scala

//> using platform native
//> using dep com.softwaremill.sttp.client4::core_native0.5:@VERSION@

import sttp.client4.*
import sttp.client4.curl.CurlBackend

@main def run(): Unit =
  val backend = CurlBackend()
  println(basicRequest.get(uri"http://httpbin.org/ip").send(backend))
```

## ZIO-based
To use in an sbt project, add the following dependency:

```
"com.softwaremill.sttp.client4" %%% "zio" % @VERSION@
```

Create the backend instance for example via `scoped()`
which will also ensure that acquired resources (if any) are released once out of `Scope`:

```scala
//> using platform native
//> using nativeVersion 0.5.10
//> using scala 3
//> using dep com.softwaremill.sttp.client4::zio::@VERSION@

import sttp.client4.*
import sttp.client4.curl.zio.CurlZioBackend
import zio.*

object Main extends ZIOAppDefault:
  def run = for
    backend <- CurlZioBackend.scoped()
    res <- basicRequest.get(uri"http://httpbin.org/ip").send(backend)
    _ <- Console.printLine(res)    
  yield ()
```

## Cats Effect-based (asynchronous)

To use in an sbt project, add the following dependency:

```
"com.softwaremill.sttp.client4" %%% "cats" % @VERSION@
```

`CurlCatsAsyncBackend` is a non-blocking backend, which uses libcurl's
[multi socket interface](https://curl.se/libcurl/c/libcurl-multi.html) (`curl_multi_socket_action`). libcurl reports
which sockets it wants to wait on and for how long; the backend delegates the waiting to the Cats Effect runtime's
polling system (epoll on Linux, kqueue on macOS). No thread is blocked on network I/O, and curl shares the event loop
with the rest of the application.

```scala
//> using platform native
//> using scala 3
//> using dep com.softwaremill.sttp.client4::cats::@VERSION@

import cats.effect.{IO, IOApp}
import sttp.client4.*
import sttp.client4.curl.cats.CurlCatsAsyncBackend

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    CurlCatsAsyncBackend.resource().use { backend =>
      basicRequest.get(uri"http://httpbin.org/ip").send(backend).flatMap(IO.println)
    }
```

`CurlCatsAsyncBackend()` creates a backend directly; it should be closed after use. The `resource()` variant closes it
for you.

### Threading

libcurl multi handles are not thread-safe, so each one is guarded by a lock, held only for the short, non-blocking
libcurl calls. When Scala Native multithreading is enabled, requests are spread over several multi handles to avoid
contention; the number can be set with the `parallelism` parameter (defaults to the number of available processors, or
`1` in single-threaded mode):

```scala
CurlCatsAsyncBackend.resource(parallelism = 2)
```

### Requirements

The backend relies on the `FileDescriptorPoller` of the runtime. The default `IORuntime` on Scala Native provides one
(epoll/kqueue); if a custom runtime doesn't, each transfer is driven from the blocking thread pool instead.

Limitations: WebSockets and `Stream`-based responses are not supported, as with the other curl backends. Responses read
using `asInputStream` work, but they are driven by the shared blocking implementation (`curl_multi_perform` +
`curl_multi_poll` on the calling thread), so they are not asynchronous.
