# Streaming

Some backends (see [backends summary](../backends/summary.md)) support streaming bodies, as described by the `Streams[S]` capability. If that's the case, you can set a stream of the supported type as a request body using the `streamBody` method, instead of the usual `body` method.

```eval_rst
.. note::

 Here, streaming refers to (usually) non-blocking, asynchronous streams of data. To send data which is available as an ``InputStream``, or a file from local storage (which is available as a ``File`` or ``Path``), no special backend support is needed. See the documenttation on :doc:`setting the request body <body>`.
```

An implementation of the `Streams[S]` capability must be passed to the `.streamBody` method, to determine the type of streams that are supported. These implementations are provided by the backend implementations, e.g. `AkkaStreams` or `Fs2Streams[F]`. 

For example, using the [akka-http backend](../backends/akka.md), a request with a streaming body can be defined as follows:

```scala mdoc:compile-only
import sttp.client3._
import sttp.capabilities.akka.AkkaStreams

import akka.stream.scaladsl.Source
import akka.util.ByteString

val chunks = "Streaming test".getBytes("utf-8").grouped(10).to(Iterable)
val source: Source[ByteString, Any] = Source.apply(chunks.toList.map(ByteString(_)))

basicRequest
  .streamBody(AkkaStreams)(source)
  .post(uri"...")
```

```eval_rst
.. note:: A request with the body set as a stream can only be sent using a backend supporting exactly the given type of streams.
```

It's also possible to specify that the [response body should be a stream](../responses/body.md#streaming).
