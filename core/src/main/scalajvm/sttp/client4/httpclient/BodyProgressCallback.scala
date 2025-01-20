package sttp.client4.httpclient

import sttp.attributes.AttributeKey

/** Defines a callback to be invoked when subsequent parts of the request body are about to be sent over the network.
  *
  * When a request is sent, `onInit` is invoked exactly once with the content length (if it is known). This is followed
  * by arbitrary number of `onNext` calls, with the number of bytes that will be sent. Finally, either `onComplete` or
  * `onError` are called exactly once.
  *
  * All of the methods should be non-blocking and complete as fast as possible, so as not to obstruct sending data over
  * the network.
  *
  * To register a callback, set the [[BodyProgressCallback.RequestAttribute]] on a request, using the
  * [[sttp.client4.Request.attribute]] method.
  */
trait BodyProgressCallback {
  def onInit(contentLength: Option[Long]): Unit

  def onNext(bytesCount: Long): Unit

  def onComplete(): Unit
  def onError(t: Throwable): Unit
}

object BodyProgressCallback {

  /** The key of the attribute that should be set on a request, to receive callbacks on the progress of sending the
    * request body.
    */
  val RequestAttribute = new AttributeKey[BodyProgressCallback](classOf[BodyProgressCallback].getName() + "_request")
}
