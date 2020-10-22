# The type of request definitions

All request definitions have type `RequestT[U, T, R]` (RequestT as in Request Template). If this looks a bit complex, don't worry, what the three type parameters stand for is the only thing you'll hopefully have to remember when using the API!

Going one-by-one:

* `U[_]` specifies if the request method and URL are specified. Using the API, this can be either `type Empty[X] = None`, meaning that the request has neither a method nor an URI. Or, it can be `type Id[X] = X` (type-level identity), meaning that the request has both a method and an URI specified. Only requests with a specified URI & method can be sent.
* `T` specifies the type to which the response will be read. By default, this is `Either[String, String]`. But it can also be e.g. `Array[Byte]` or `Unit`, if the response should be ignored. Response body handling can be changed by calling the `.response` method. With backends which support streaming, this can also be a supported stream type. See [response body specifications](../responses/body.md) for more details.
* `R` specifies the requirements of this request. Most of the time this will be `Any`, meaning that this request does not have any special requirements, and can be sent using any backend. So most of the time you can just ignore that parameter. However, if you are using streaming [request](streaming.md)/[response](../responses/body.md) bodies or [websockets](../websockets.md), the type parameter will reflect the required capabilities. They can include `Effect[F]`, `Streams[S]` and `WebSockets`.

There are two type aliases for the request template that are used:

* `type Request[T, R] = RequestT[Identity, T, R]`. A sendable request.
* `type PartialRequest[T, R] = RequestT[Empty, T, R]`

As `basicRequest`, the starting request, by default reads the body into a `Either[String, String]`, its type is:

`basicRequest: PartialRequest[Either[String, String], Any]`
