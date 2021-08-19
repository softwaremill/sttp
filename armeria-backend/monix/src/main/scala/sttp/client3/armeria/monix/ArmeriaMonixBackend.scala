package sttp.client3.armeria.monix

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.reactivestreams.Publisher
import sttp.capabilities.monix.MonixStreams
import sttp.client3.armeria.AbstractArmeriaBackend.newClient
import sttp.client3.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client3.impl.monix.TaskMonadAsyncError
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadAsyncError

private final class ArmeriaMonixBackend(client: WebClient, closeFactory: Boolean)(implicit scheduler: Scheduler)
    extends AbstractArmeriaBackend[Task, MonixStreams](client, closeFactory, TaskMonadAsyncError) {

  override val streams: MonixStreams = MonixStreams

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[Task, MonixStreams] =
    new BodyFromStreamMessage[Task, MonixStreams] {

      override val streams: MonixStreams = MonixStreams

      override implicit def monad: MonadAsyncError[Task] = TaskMonadAsyncError

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): Observable[Array[Byte]] =
        Observable.fromReactivePublisher(streamMessage).map(_.array())
    }

  override protected def streamToPublisher(stream: Observable[Array[Byte]]): Publisher[HttpData] =
    stream.map(HttpData.wrap).toReactivePublisher
}

object ArmeriaMonixBackend {

  /** Creates a new Armeria backend, using the given or default `SttpBackendOptions`. Due to these customisations, the
    * client will manage its own connection pool. If you'd like to reuse the default Armeria
    * [[https://armeria.dev/docs/client-factory ClientFactory]] use `.usingDefaultClient`.
    * @param scheduler
    *   The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def apply(options: SttpBackendOptions = SttpBackendOptions.Default)(implicit
      scheduler: Scheduler = Scheduler.global
  ): SttpBackend[Task, MonixStreams] =
    apply(newClient(options), closeFactory = true)

  /** @param scheduler The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def usingClient(client: WebClient)(implicit
      scheduler: Scheduler = Scheduler.global
  ): SttpBackend[Task, MonixStreams] =
    apply(client, closeFactory = false)

  /** @param scheduler The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def usingDefaultClient()(implicit
      scheduler: Scheduler = Scheduler.global
  ): SttpBackend[Task, MonixStreams] =
    apply(newClient(), closeFactory = false)

  private def apply(client: WebClient, closeFactory: Boolean)(implicit
      scheduler: Scheduler
  ): SttpBackend[Task, MonixStreams] =
    new FollowRedirectsBackend(new ArmeriaMonixBackend(client, closeFactory))
}
