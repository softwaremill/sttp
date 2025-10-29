package sttp.client4

import sttp.model.Method

object RetryWhen {
  def isBodyRetryable(body: GenericRequestBody[_]): Boolean =
    body match {
      case NoBody              => true
      case _: StringBody       => true
      case _: ByteArrayBody    => true
      case _: ByteBufferBody   => true
      case _: InputStreamBody  => false
      case _: FileBody         => true
      case StreamBody(_)       => false
      case m: MultipartBody[_] => m.parts.forall(p => isBodyRetryable(p.body))
    }

  /** The default implementation of a predicate, checking if a request can be retried.
    *
    * A request can be retried if:
    *   - there was a connection error (hence, the request was never sent)
    *   - the response was a server error (5xx, not 4xx - which is a client's error)
    *   - the request's method was idempotent (e.g. GET, but not POST)
    *   - the body can be sent again (e.g. not a stream)
    */
  val Default: RetryWhen = {
    case (_, Left(_: SttpClientException.ConnectException)) => true
    case (_, Left(_))                                       => false
    case (request, Right(response))                         =>
      isBodyRetryable(request.body) && Method.isIdempotent(request.method) && response.code.isServerError
  }
}
