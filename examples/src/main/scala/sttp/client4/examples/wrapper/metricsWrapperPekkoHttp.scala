// {cat=Backend wrapper; effects=Future; backend=Pekko}: Report metrics to a cloud service

//> using dep com.softwaremill.sttp.client4::pekko-http-backend:4.0.0-M20
//> using dep org.apache.pekko::pekko-stream:1.1.2

package sttp.client4.examples.wrapper

import sttp.attributes.AttributeKey
import sttp.capabilities.Effect
import sttp.client4.*
import sttp.client4.pekkohttp.*
import sttp.client4.wrappers.DelegateBackend
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.*
import scala.concurrent.Await
import scala.concurrent.duration.Duration

// the metrics infrastructure
trait MetricsServer:
  def reportDuration(name: String, duration: Long): Future[Unit]

class CloudMetricsServer extends MetricsServer:
  override def reportDuration(name: String, duration: Long): Future[Unit] =
    // here, a call to the cloud service should be made
    Future.successful(println(s"Reporting metric $name: $duration"))

// attributes used by the wrapper to enrich metrics
case class MetricPrefix(prefix: String)
val MetricPrefixAttributeKey = AttributeKey[MetricPrefix]

// the backend wrapper - works for any Future-based backend
abstract class MetricWrapper[P](delegate: GenericBackend[Future, P], metrics: MetricsServer)
    extends DelegateBackend(delegate):

  override def send[T](request: GenericRequest[T, P with Effect[Future]]): Future[Response[T]] =
    val start = System.currentTimeMillis()

    def report(metricSuffix: String): Future[Unit] =
      val metricPrefix = request.attribute(MetricPrefixAttributeKey).getOrElse(MetricPrefix("?"))
      val end = System.currentTimeMillis()
      metrics.reportDuration(metricPrefix.prefix + "-" + metricSuffix, end - start)

    delegate
      .send(request)
      .transformWith:
        case Success(response) if response.is200 => report("ok").map(_ => response)
        case Success(response)                   => report("notok").map(_ => response)
        case Failure(t)                          => report("exception").flatMap(_ => Future.failed(t))

object MetricWrapper:
  def apply[S](
      backend: WebSocketStreamBackend[Future, S],
      metrics: MetricsServer
  ): WebSocketStreamBackend[Future, S] =
    new MetricWrapper(backend, metrics) with WebSocketStreamBackend[Future, S] {}

// example usage
@main def metricsWrapperPekkoHttp(): Unit =
  val backend = MetricWrapper(PekkoHttpBackend(), new CloudMetricsServer())

  def request1 = basicRequest
    .get(uri"https://httpbin.org/status/200")
    .attribute(MetricPrefixAttributeKey, MetricPrefix("service1"))
    .send(backend)

  def request2 = basicRequest
    .get(uri"https://httpbin.org/status/404")
    .attribute(MetricPrefixAttributeKey, MetricPrefix("service1"))
    .send(backend)

  val _ = Await.result(request1.flatMap(_ => request2).flatMap(_ => backend.close()), Duration.Inf)
