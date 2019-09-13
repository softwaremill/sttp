package com.softwaremill.sttp

import com.softwaremill.sttp.monad.FutureMonad

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Promise
import org.scalajs.dom.experimental.BodyInit
import org.scalajs.dom.experimental.{Response => FetchResponse}

class FetchBackend private (fetchOptions: FetchOptions)(implicit ec: ExecutionContext)
    extends AbstractFetchBackend[Future, Nothing](fetchOptions)(new FutureMonad()) {

  override protected def addCancelTimeoutHook[T](result: Future[T], cancel: () => Unit): Future[T] = {
    result.onComplete(_ => cancel())
    result
  }

  override protected def handleStreamBody(s: Nothing): Future[js.UndefOr[BodyInit]] = {
    // we have an instance of nothing - everything's possible!
    Future.successful(js.undefined)
  }

  override protected def handleResponseAsStream[T](
      ras: ResponseAsStream[T, Nothing],
      response: FetchResponse
  ): Future[Nothing] = {
    throw new IllegalStateException("Future FetchBackend does not support streaming responses")
  }

  override protected def transformPromise[T](promise: => Promise[T]): Future[T] = promise.toFuture

}

object FetchBackend {

  def apply(
      fetchOptions: FetchOptions = FetchOptions.Default
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing] =
    new FetchBackend(fetchOptions)
}
