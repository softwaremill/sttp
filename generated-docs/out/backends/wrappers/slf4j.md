# Logging using slf4j

There are three backend wrappers available, which log request & response information using a slf4j `Logger`. To see the logs, you'll need to use an slf4j-compatible logger implementation, e.g.  [logback](http://logback.qos.ch), or use a binding, e.g. [log4j-slf4j](https://logging.apache.org/log4j/2.0/log4j-slf4j-impl/index.html).

To use the backend wrappers, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "slf4j-backend" % "2.2.4"
``` 

The following backend wrappers are available:

```scala
import sttp.client._
import sttp.client.logging.slf4j._
val delegateBackend: SttpBackend[Identity, Nothing] = ???

Slf4jLoggingBackend(delegateBackend)
Slf4jTimingBackend(delegateBackend)
Slf4jCurlBackend(delegateBackend)
```

The logging backend logs `DEBUG`-level logs when a request is started, completes successfully, and `ERROR`-level logs when it results in an exception.

The timing backend logs `INFO`-level logs when a request completes successfully or with an exception, together with the number of seconds and milliseconds that the request took.

The curl backend logs `DEBUG`-level logs when a request completes successfully or with an exception, together with the curl command that can be issued to reproduce the request.

Example usage:

```scala
import sttp.client._
import sttp.client.logging.slf4j.Slf4jTimingBackend

val backend = Slf4jTimingBackend(HttpURLConnectionBackend())
basicRequest.get(uri"https://httpbin.org/get").send(backend)
```

To create a customised logging backend, see the section on [custom backends](custom.md).
