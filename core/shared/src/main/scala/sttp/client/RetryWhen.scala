package sttp.client

import sttp.model.Method

object RetryWhen {
  def isBodyRetryable(body: RequestBody[_]): Boolean = {
    body match {
      case NoBody               => true
      case _: StringBody        => true
      case _: ByteArrayBody     => true
      case _: ByteBufferBody    => true
      case _: InputStreamBody   => false
      case _: FileBody          => true
      case StreamBody(_)        => false
      case MultipartBody(parts) => parts.forall(p => isBodyRetryable(p.body))
    }
  }

  val Default: RetryWhen = {
    // we dont't know what kind of exception that is. When #419 is implemented, we can return true for connection
    // exceptions (when we are sure that no data has been sent)
    case (_, Left(_)) => false
    case (request, Right(_)) =>
      isBodyRetryable(request.body) && Method.isIdempotent(request.method)
  }

}
