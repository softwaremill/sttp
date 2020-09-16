# How sttp client works

## Describe the request

This first step when using sttp client is describing the request that you'd like to send. 

A request is represented as an immutable data structure of type `RequestT` (as in Request Template). The basic request is provided as the `basicRequest` value, in the `sttp.client3` package. It can be refined using one of the available methods, such as `.header`, `.body`, `.get(Uri)`, `.responseAs`, etc.

A `RequestT` value contains both information on what to include in the request, but also how to handle the response body. 

To start describing a request, import the sttp client package and customise `basicRequest`:

```scala
import sttp.client3._
val myRequest: Request[_, _] = ??? // basicRequest.(...)
```

An alternative to importing the `sttp.client3._` package, is to extend the `sttp.client3.SttpApi` trait. That way, multiple integrations can be grouped in one object, thus reducing the number of necessary imports.

## Send the request

Once the request is described as a value, it can be sent. To send a request, you'll need an `SttpBackend`. 

The backend is where most of the work happens: the request is translated to a backend-specific form; a connection is opened, data sent and received; finally, the backend-specific response is translated to sttp's `Response`, as described in the request.

A backend can be synchronous, that is, sending a request can be a blocking operation. When invoking `myRequest.send(backend)`, you'll get a value of type `Response[T]`. Backends can also be asynchronous, and evaluate the send operation eagerly or lazily. For example, when using the [Akka backend](backends/akka.md), `myRequest.send(backend)` will return a `Future[Response[T]]`: an eagerly-evaluated, asynchronous result. When using a [Monix backend](backends/monix.md), you'll get back a `Task[Response[T]]`: a lazily-evaluated, but also non-blocking and asynchronous result. 

Backends manage the connection pool, thread pools for handling responses, depending on the implementation provide various configuration options, and optionally support [streaming](requests/streaming.md) and [websockets](websockets.md). They typically need to be created upon application startup, and closed when the application terminates. 

For example, the following sends a synchronous request, using the default JVM backend:

```scala
import sttp.client3._
val myRequest: Request[String, Any] = ???
val backend = HttpURLConnectionBackend()
val response = myRequest.send(backend)
```

## Next steps

Read more about:

* [describing the request](requests/basics.md)
* the [`RequestT` type](requests/type.md)
* specifying how to handle the [response body](responses/body.md)
* [available backends](backends/summary.md)
