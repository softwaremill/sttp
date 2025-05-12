# Scala Native (curl) backend

A Scala Native (0.5.x) backend implemented using [Curl](https://github.com/curl/curl/blob/master/include/curl/curl.h).

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %%% "core" % "4.0.5"
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
//> using dep com.softwaremill.sttp.client4::core_native0.5:4.0.5

import sttp.client4.*
import sttp.client4.curl.CurlBackend

@main def run(): Unit =
  val backend = CurlBackend()
  println(basicRequest.get(uri"http://httpbin.org/ip").send(backend))
```

