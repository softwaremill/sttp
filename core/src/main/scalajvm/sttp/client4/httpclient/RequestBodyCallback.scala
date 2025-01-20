package sttp.client4.httpclient

import sttp.attributes.AttributeKey

import java.nio.ByteBuffer

/** Defines a callback to be invoked when subsequent parts of the request body to be sent are created, just before they
  * are sent over the network.
  *
  * When a request is sent, `onInit` is invoked exactly once with the content length (if it is known). This is followed
  * by arbitrary number of `onNext` calls. Finally, either `onComplete` or `onError` are called exactly once.
  *
  * All of the methods should be non-blocking and complete as fast as possible, so as not to obstruct sending data over
  * the network.
  *
  * To register a callback, set the [[RequestBodyCallback.Attribute]] on a request, using the
  * [[sttp.client4.Request.attribute]] method.
  */
trait RequestBodyCallback {
  def onInit(contentLength: Option[Long]): Unit

  def onNext(b: ByteBuffer): Unit

  def onComplete(): Unit
  def onError(t: Throwable): Unit
}

object RequestBodyCallback {

  /** The key of the attribute that should be set on a request, to receive callbacks when the request body is sent. */
  val Attribute = AttributeKey[RequestBodyCallback]
}
