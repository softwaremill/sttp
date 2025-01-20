# Body callbacks

When using the `HttpClient`-based backends (which includes the `DefaultSyncBackend` and `DefaultFutureBackend` on the
JVM), it is possible to register body-related callbacks.

This feature is not available in other backends, and setting the attribute described below will have no effect.

## Request body callbacks

Defines a callback to be invoked when subsequent parts of the request body to be sent are created, just before they
are sent over the network. The callback is defined through an instance of the `RequestBodyCallback` trait.

When a request is sent, the `RequestBodyCallback.onInit` method is invoked exactly once with the content length (if it 
is known). This is followed by arbitrary number of `onNext` calls. Finally, either `onComplete` or `onError` are called 
exactly once.

All of the methods in the `RequestBodyCallback` implementation should be non-blocking and complete as fast as possible, 
so as not to obstruct sending data over the network.

To register a callback, set the `RequestBodyCallback.Attribute` on a request. For example:

```scala mdoc:compile-only
import sttp.client4.*
import sttp.client4.httpclient.{HttpClientSyncBackend, RequestBodyCallback}
import java.nio.ByteBuffer
import java.io.File

val backend = HttpClientSyncBackend()

val fileToSend: File = ???
val callback = new RequestBodyCallback {
  override def onInit(contentLength: Option[Long]): Unit = println(s"expected content length: $contentLength")
  override def onNext(b: ByteBuffer): Unit = println(s"next, bytes: ${b.remaining()}")
  override def onComplete(): Unit = println(s"complete")
  override def onError(t: Throwable): Unit = println(s"error: ${t.getMessage}")
}

val response = basicRequest
  .get(uri"http://example.com")
  .body(fileToSend)
  .attribute(RequestBodyCallback.Attribute, callback)
  .send(backend)
```