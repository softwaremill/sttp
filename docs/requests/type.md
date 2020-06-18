# The type of request definitions

All request definitions have type `RequestT[U, T, S]` (RequestT as in Request Template). If this looks a bit complex, don't worry, what the three type parameters stand for is the only thing you'll hopefully have to remember when using the API!

Going one-by-one:

* `U[_]` specifies if the request method and URL are specified. Using the API, this can be either `type Empty[X] = None`, meaning that the request has neither a method nor an URI. Or, it can be `type Id[X] = X` (type-level identity), meaning that the request has both a method and an URI specified. Only requests with a specified URI & method can be sent.
* `T` specifies the type to which the response will be read. By default, this is `Either[String, String]`. But it can also be e.g. `Array[Byte]` or `Unit`, if the response should be ignored. Response body handling can be changed by calling the `.response` method. With backends which support streaming, this can also be a supported stream type. See [response body specifications](../responses/body.md) for more details.
* `S` specifies the stream type that this request uses. Most of the time this will be `Nothing`, meaning that this request does not send a streaming body or receive a streaming response. So most of the time you can just ignore that parameter. But, if you are using a streaming backend and want to send/receive a stream, the `.streamBody` or `response(asStream[S])` will change the type parameter.

There are two type aliases for the request template that are used:

* `type Request[T, S] = RequestT[Id, T, S]`. A sendable request.
* `type PartialRequest[T, S] = RequestT[Empty, T, S]`

As `basicRequest`, the starting request, by default reads the body into a `Either[String, String]`, its type is:

```scala
basicRequest: PartialRequest[Either[String, String], Nothing]
```
