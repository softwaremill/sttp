# Exceptions

HTTP requests might fail in a variety of ways! There are two basic types of failures that might occur:

* network-level failure, such as the invalid/unroutable hosts, inability to establish a TCP connection, or broken sockets
* protocol-level failure, represented as 4xx and 5xx responses

The first type of failures is represented by exceptions, which are thrown when sending the request (using `request.send(backend)`). The second type of failure is represented as a `Response[T]`, with the appropriate response code. The response body might depend on the status code; by default the response is read as a `Either[String, String]`, where the left side represents protocol-level failure, and the right side: success.

Exceptions might be thrown directly (synchronous, direct-style backends), or returned as failed effects (other backends, e.g. failed `scala.concurrent.Future`). Backends will try to categorize these exceptions into a `SttpClientException`, which has two main subclass branches:

* `ConnectException`: when a connection (tcp socket) can't be established to the target host
* `ReadException`: when a connection has been established, but there's any kind of problem receiving the response (e.g. a broken socket)

Read exceptions might be further categorized into:

* `TimeoutException`: any kind of timeout when reading
* `TooManyRedirectsException`: throw by the [follow-redirects](../conf/redirects.md) backend
* `ResponseHandlingException`: wrapping a [[ResponseException]] (exception that occurs when handling the response body, using e.g. `asJson`)

In general, it's safe to assume that the request hasn't been sent in case of connect exceptions. With read exceptions, the target host might or might not have received and processed the request.

Unknown exceptions aren't categorized and are re-thrown unchanged.

## Response-handling, deserialization errors

Exceptions might be thrown when deserializing the response body - depending on the specification of how to handle response bodies. The built-in deserializers (see e.g. [json](../other/json.md)) return errors represented as `ResponseException[HE]`, which can either be a `UnexpectedStatusCode` (protocol-level failures, containing a potentially deserialized body value) or a `DeserializationException` (containing a deserialization-library-specific exception).

This means that a typical `asJson` response specification will result in the body being read as:

```scala
import sttp.client4.*
def asJson[T]: ResponseAs[Either[ResponseException[String], T]] = ???
``` 

There are also the `asJsonOrFail`, `asStringOrFail` etc. response descriptions, which handle http errors or deserialization exceptions by throwing an exception / returning a failed effect. Alternatively, you can use the `.orFail` and `.orFailDeserialization` methods on any response description that is an `Either`.

## Possible outcomes

Summing up, when the response is deserialized (e.g. to json), sending a request can have these outcomes, and can be represented in the following ways:

* network-level success (HTTP request sent and response received)
  * http error (4xx, 5xx), successfully parsed (**a value wrapped in `Left`, or a failed effect**)
  * http success (2xx), successfully parsed (**a value possibly wrapped in `Right`**)
  * deserialization error (**a value wrapped in `Left`, or a failed effect**)
* network-level failure (invalid host, broken socket): **failed effect**
