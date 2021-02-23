package sttp.client3.armeria.scalaz

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import org.reactivestreams.Publisher
import scalaz.concurrent.Task
import sttp.client3.armeria.AbstractArmeriaBackend.newClient
import sttp.client3.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client3.impl.scalaz.TaskMonadAsyncError
import sttp.client3.internal.NoStreams
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadAsyncError

private final class ArmeriaScalazBackend(client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[Task, Nothing](client, closeFactory, TaskMonadAsyncError) {

  override val streams: NoStreams = NoStreams

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[Task, Nothing] =
    new BodyFromStreamMessage[Task, Nothing] {

      override val streams: NoStreams = NoStreams

      override implicit val monad: MonadAsyncError[Task] = TaskMonadAsyncError

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): Nothing =
        throw new UnsupportedOperationException("This backend does not support streaming")
    }

  override protected def streamToPublisher(stream: Nothing): Publisher[HttpData] =
    throw new UnsupportedOperationException("This backend does not support streaming")
}

object ArmeriaScalazBackend {
  /** Creates a new Armeria backend, using the given or default `SttpBackendOptions`. Due to these customisations,
    * the client will manage its own connection pool. If you'd like to reuse the default Armeria `ClientFactory`,
    * use `.usingDefaultClient`. */
  def apply(options: SttpBackendOptions = SttpBackendOptions.Default): SttpBackend[Task, Any] =
    apply(newClient(options), closeFactory = true)

  def usingClient(client: WebClient): SttpBackend[Task, Any] = apply(client, closeFactory = false)

  def usingDefaultClient(): SttpBackend[Task, Any] = apply(newClient(), closeFactory = false)

  private def apply(
      client: WebClient,
      closeFactory: Boolean
  ): SttpBackend[Task, Any] =
    new FollowRedirectsBackend(new ArmeriaScalazBackend(client, closeFactory))
}
