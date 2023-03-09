# 4. Separate request types

Date: 2023-02-23

## Context

Sttp base types are [being refactored](https://github.com/softwaremill/sttp/pull/1703) to decrease the learning curve, 
and generally improve the developer experience.

## Decision

The `RequestT` type is being split into a type hierarchy, with new subtypes representing requests requiring different
capabilities.

The requests are split into request builders: top-level `PartialRequest` and traits with common methods: 
`PartialRequestBuilder` and `RequestBuilder`.

Additionally, we've got a base trait for a generic request (that is a request description, which includes the uri
and method), `GenericRequest`. This is then implemented by a number of top-level classes: `Request`, `StreamRequest`,
`WebSocketRequest` and `WebSocketStreamRequest`. The capabilities supported by these requests are fixed. Setting
a response description or an input stream body promotes a `Request` or `PartialRequest` to the appropriate type.

### What do we gain

The main gain is that the types are simpler. Before, the `RequestT[U, T, R]` type had three type parameters:
a marker type constructor, specifying if the uri/method are specified; the target type, to which the response is
deserialized; and the set of required capabilities.

With the new design, the base `Request[T]` type has one type parameter only (response target type). This is expanded
to two type parameters in the other request types, to express the additional requirements.

Moreover, the specific request types clearly specify what type of `Backend` is required to send the request.

### What do we loose

It is harder to write generic methods which manipulate *any* request description (including partial request). This
is still possible, by properly parametrizing the method with the subtype of `PartialRequestBuilder` used, but might
not be as straightforward as before.

Moreover, integration with Tapir might be more challenging, as we cannot simply accept an endpoint with a set of
capabilities, and produce a request with a mirror capability set. Special-casing on streaming/websocket capabilities
will be required.

Finally, some flexibility is lost, as there are no partial streaming/websocket requests. That is, the method & uri
need to be specified on the request *before* specifying that the body should be streamed or that the response
should be a web socket one. This could be amended by introducing additional `PartialRequest` subtypes, however it is
not clear right now that it is necessary.