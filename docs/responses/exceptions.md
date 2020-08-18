# Exceptions

HTTP requests might fail in a variety of ways! There are two basic types of failures that might occur:

* network-level failure, such as the invalid/unroutable hosts, inability to establish a TCP connection, or broken sockets
* protocol-level failure, represented as 4xx and 5xx responses

The first type of failures is represented by exceptions, which are thrown when sending the request (using `request.send(backend)`). The second type of failure is represented as a `Response[T]`, with the appropriate response code. The response body might depend on the status code; by default the response is read as a `Either[String, String]`, where the left side represents protocol-level failure, and the right side: success.

Exceptions might be thrown directly (`Identity` synchronous backends), or returned as failed effects (other backends, e.g. failed `scala.concurrent.Future`). Backends will try to categorise these exceptions into a `SttpClientException`, which has two subclasses:

* `ConnectException`: when a connection (tcp socket) can't be established to the target host
* `ReadException`: when a connection has been established, but there's any kind of problem receiving the response (e.g. a broken socket)

In general, it's safe to assume that the request hasn't been sent in case of connect exceptions. With read exceptions, the target host might or might have not received and processed the request.

Unknown exceptions aren't categorised and are re-thrown unchanged.

## Deserialisation errors

Exceptions might also be thrown when deserializing the response body - depending on the specification on how to handle response bodies. The built-in deserialisers (e.g. [json](../json.md) ones) return errors represented as `ResponseError[T]`, which can either be a `HttpError` (protocol-level failures) or a `DeserializationError` (a deserialisation-library-specific exception).

This means that a typical `asJson` response specification will result in the body being read as:

```scala mdoc:silent
import sttp.client._
def asJson[T]: ResponseAs[Either[ResponseError[Exception], T], Any] = ???
``` 

There are also "unsafe" variants of the built-in response specification which represent deserialisation exceptions as failed effects / throw them synchronously.