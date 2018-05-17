package com.softwaremill.sttp.asynchttpclient.scalaz

import java.nio.ByteBuffer

import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import com.softwaremill.sttp.impl.scalaz.TaskMonadAsyncError
import com.softwaremill.sttp.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import io.netty.buffer.ByteBuf
import org.asynchttpclient.{AsyncHttpClient, AsyncHttpClientConfig, DefaultAsyncHttpClient}
import org.reactivestreams.Publisher

import scalaz.concurrent.Task

class AsyncHttpClientScalazBackend private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)
    extends AsyncHttpClientBackend[Task, Nothing](asyncHttpClient, TaskMonadAsyncError, closeClient) {

  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")

  override protected def publisherToString(p: Publisher[ByteBuffer]): Task[String] =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientScalazBackend {
  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean): SttpBackend[Task, Nothing] =
    new FollowRedirectsBackend[Task, Nothing](new AsyncHttpClientScalazBackend(asyncHttpClient, closeClient))

  def apply(options: SttpBackendOptions = SttpBackendOptions.Default): SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  def usingConfig(cfg: AsyncHttpClientConfig): SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend(new DefaultAsyncHttpClient(cfg), closeClient = true)

  def usingClient(client: AsyncHttpClient): SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend(client, closeClient = false)
}
