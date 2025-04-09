package sttp.client3.armeria.future

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import org.reactivestreams.Publisher
import sttp.client3.armeria.ArmeriaWebClient.newClient
import sttp.client3.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client3.internal.NoStreams
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.{FutureMonad, MonadAsyncError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private final class ArmeriaFutureBackend(client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[Future, Nothing](client, closeFactory, new FutureMonad()) {

  override val streams: NoStreams = NoStreams

  override protected def ensureOnAbnormal[T](effect: Future[T])(finalizer: => Future[Unit]): Future[T] =
    effect.recoverWith { case e =>
      finalizer.recoverWith { case e2 => e.addSuppressed(e2); Future.failed(e) }.flatMap(_ => Future.failed(e))
    }

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

  /** Creates a new Armeria backend, using the given or default `SttpBackendOptions`. Due to these customisations, the
    * client will manage its own connection pool. If you'd like to reuse the default Armeria
    * [[https://armeria.dev/docs/client-factory ClientFactory]] use `.usingDefaultClient`.
    */
  def apply(options: SttpBackendOptions = SttpBackendOptions.Default): SttpBackend[Future, Any] =
    apply(newClient(options), closeFactory = true)

  def usingClient(client: WebClient): SttpBackend[Future, Any] =
    apply(client, closeFactory = false)

  def usingDefaultClient(): SttpBackend[Future, Any] =
    apply(newClient(), closeFactory = false)

  private def apply(client: WebClient, closeFactory: Boolean): SttpBackend[Future, Any] =
    new FollowRedirectsBackend(new ArmeriaFutureBackend(client, closeFactory))
}
