# Streaming

Some backends (see [backends summary](../backends/summary.md)) support streaming bodies, as described by the `Streams[S]` capability. If that's the case, you can set a stream of the supported type as a request body using the `streamBody` method, instead of the usual `body` method.

```{note}
Here, streaming refers to (usually) non-blocking, asynchronous streams of data. To send data which is available as an `InputStream`, or a file from local storage (which is available as a `File` or `Path`), no special backend support is needed. See the documenttation on [setting the request body](body.md).
```

An implementation of the `Streams[S]` capability must be passed to the `.streamBody` method, to determine the type of streams that are supported. These implementations are provided by the backend implementations, e.g. `PekkoStreams` or `Fs2Streams[F]`. 

For example, using the [pekko-http backend](../backends/pekko.md), a request with a streaming body can be defined as follows:

```scala
import sttp.client4.*
import sttp.capabilities.pekko.PekkoStreams

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

val chunks = "Streaming test".getBytes("utf-8").grouped(10).to(Iterable)
val source: Source[ByteString, Any] = Source.apply(chunks.toList.map(ByteString(_)))

basicRequest
  .post(uri"...")
  .streamBody(PekkoStreams)(source)
```

```{note}
A request with the body set as a stream can only be sent using a backend supporting exactly the given type of streams.
```

It's also possible to specify that the [response body should be a stream](../responses/body.md).
