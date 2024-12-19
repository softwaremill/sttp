# Timeouts

sttp supports read and connection timeouts:

* Connection timeout - can be set globally (30 seconds by default)
* Read timeout - can be set per request (1 minute by default)

How to use:

```scala
import sttp.client4.*
import scala.concurrent.duration.*

// all backends provide a constructor that allows to specify backend options
val backend = DefaultSyncBackend(
  options = BackendOptions.connectionTimeout(1.minute))

basicRequest
  .get(uri"...")
  .readTimeout(5.minutes) // or Duration.Inf to turn read timeout off
  .send(backend)
```
