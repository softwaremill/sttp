# 1. Extract stream from http4s

Date: 2019-10-04

## Context

When getting the response as a stream from the http4s backend, the underlying connection is closed as soon as
the effect reading the response is finished. That is, before the user had a chance to consume the stream.

## Decision

In http4s, all interaction with the response body is protected: the response stream *must* be consumed
entirely into an effect by a provided function `Stream[F, Byte] => F[T]`. This is different than in sttp:
if the user wants to get the response as a stream, it is up to the user to consume the stream. Implicitly,
there's an assumption that the underlying connection will be closed only after the whole stream is consumed.

http4s has "internal" consumption of the stream, while sttp - external.

This way of consuming streams is less safe, but that's a design decision of sttp's client API. Hence, we need
to adjust the way http4s works and "extract" the stream from http4s, releasing the connection only when the
stream is fully read (or completes with an error).

To do that, in `Http4sBackend`, we create two `MVar`s: one to signal that the request body has been fully
consumed (`responseBodyCompleteVar`), and another, `responseVar`, to extract the response.

When the request is read, first the stream body is adjusted, so that the completion var is filled in the stream's
finalizer. Then, we create the response and fill the `responseVar` with the response value. Finally, we read from
the completion, so that http4s closes the connection only when the response is read. The ordering of these effects
is crucial, so that we don't deadlock!

That process is started in the background (`sendRequest.start`). In the foreground, we read the content of the
`responseVar` and return it the user as soon as it's available.
