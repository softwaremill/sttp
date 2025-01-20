# Request body progress callback

When using the `HttpClient`-based backends (which includes the `DefaultSyncBackend` and `DefaultFutureBackend` on the
JVM), it is possible to register a callback that keeps track of the progress of sending the request body.

This feature is not available in other backends, and setting the attribute described below will have no effect.

The callback is defined through an instance of the `RequestBodyProgressCallback` trait.

When a request is sent, the `RequestBodyProgressCallback.onInit` method is invoked exactly once with the content length (if it 
is known). This is followed by arbitrary number of `onNext` calls. Finally, either `onComplete` or `onError` are called 
exactly once.

```{note}
`onNext` is called when a part of the request body is ready to be sent over the network, that is, before it is actually
being transferred.
```

All of the methods in the `RequestBodyProgressCallback` implementation should be non-blocking and complete as fast as possible, 
so as not to obstruct sending data over the network.

To register a callback, set the `RequestBodyProgressCallback.Attribute` on a request. For example:

```scala mdoc:compile-only
import sttp.client4.*
import sttp.client4.httpclient.{HttpClientSyncBackend, RequestBodyProgressCallback}
import java.io.File

val backend = HttpClientSyncBackend()

val fileToSend: File = ???
val callback = new RequestBodyProgressCallback {
  override def onInit(contentLength: Option[Long]): Unit = println(s"expected content length: $contentLength")
  override def onNext(bytesCount: Long): Unit = println(s"next, bytes: $bytesCount")
  override def onComplete(): Unit = println(s"complete")
  override def onError(t: Throwable): Unit = println(s"error: ${t.getMessage}")
}

val response = basicRequest
  .get(uri"http://example.com")
  .body(fileToSend)
  .attribute(RequestBodyProgressCallback.Attribute, callback)
  .send(backend)
```