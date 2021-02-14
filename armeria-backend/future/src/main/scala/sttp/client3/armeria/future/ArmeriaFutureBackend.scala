package sttp.client3.armeria.future

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import org.reactivestreams.Publisher
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import sttp.client3.armeria.AbstractArmeriaBackend.newClient
import sttp.client3.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client3.internal.NoStreams
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.{FutureMonad, MonadAsyncError}

private final class ArmeriaFutureBackend(client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[Future, Nothing](client, closeFactory, new FutureMonad()) {

  override val streams: NoStreams = NoStreams

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[Future, Nothing] =
    new BodyFromStreamMessage[Future, Nothing] {

      override val streams: NoStreams = NoStreams

      override implicit def monad: MonadAsyncError[Future] = new FutureMonad()

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): streams.BinaryStream =
        throw new UnsupportedOperationException("This backend does not support streaming")
    }

  override protected def streamToPublisher(stream: streams.BinaryStream): Publisher[HttpData] =
    throw new UnsupportedOperationException("This backend does not support streaming")
}

object ArmeriaFutureBackend {

  /** Creates a new `SttpBackend`.
    */
  def apply(): SttpBackend[Future, Any] = apply(newClient(), closeFactory = false)

  /** Creates a new `SttpBackend` with the specified `SttpBackendOptions`. */
  def apply(options: SttpBackendOptions): SttpBackend[Future, Any] =
    apply(newClient(options), closeFactory = true)

  /** Creates a new `SttpBackend` with the specified `WebClient`. */
  def usingClient(client: WebClient): SttpBackend[Future, Any] =
    apply(client, closeFactory = false)

  private def apply(client: WebClient, closeFactory: Boolean): SttpBackend[Future, Any] =
    new FollowRedirectsBackend(new ArmeriaFutureBackend(client, closeFactory))
}
