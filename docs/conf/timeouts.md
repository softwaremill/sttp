# Timeouts

sttp supports read and connection timeouts:

* Connection timeout - can be set globally (30 seconds by default)
* Read timeout - can be set per request (1 minute by default)

How to use:

```scala mdoc:compile-only
import sttp.client4._
import sttp.client4.httpclient.HttpClientSyncBackend
import scala.concurrent.duration._

// all backends provide a constructor that allows to specify backend options
val backend = HttpClientSyncBackend(
  options = BackendOptions.connectionTimeout(1.minute))

basicRequest
  .get(uri"...")
  .readTimeout(5.minutes) // or Duration.Inf to turn read timeout off
  .send(backend)
```
