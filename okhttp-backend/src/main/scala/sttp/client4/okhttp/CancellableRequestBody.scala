package sttp.client4.okhttp

/** A request body backed by a running stream, which should be cancelled when the request is cancelled. */
// cancelling an OkHttp call closes the connection, but doesn't interrupt `RequestBody.writeTo` waiting for stream data
private[okhttp] trait CancellableRequestBody {
  def cancel(): Unit
}
