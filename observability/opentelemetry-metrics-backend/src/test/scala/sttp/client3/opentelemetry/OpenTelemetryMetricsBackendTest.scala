package sttp.client3.opentelemetry

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.{HistogramPointData, MetricData}
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.testing.SyncBackendStub
import sttp.client3.{DeserializationException, HttpError, Response, UriContext, asString, basicRequest}
import sttp.model.{Header, StatusCode}

import scala.collection.JavaConverters._
import scala.collection.immutable._

class OpenTelemetryMetricsBackendTest extends AnyFlatSpec with Matchers with OptionValues {

  private def spawnNewOpenTelemetry(reader: InMemoryMetricReader) = {
    val mockMeter: SdkMeterProvider =
      SdkMeterProvider.builder().registerMetricReader(reader).build()

    OpenTelemetrySdk
      .builder()
      .setMeterProvider(mockMeter)
      .build()
  }

  val stubAlwaysOk = SyncBackendStub.whenAnyRequest.thenRespondOk()

  "OpenTelemetryMetricsBackend" should "use default names" in {
    // given
    val requestsNumber = 10
    val reader = InMemoryMetricReader.create()
    val backend = OpenTelemetryMetricsBackend(stubAlwaysOk, spawnNewOpenTelemetry(reader))

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/echo")))

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName).value shouldBe requestsNumber
  }

  it should "zero the number of in-progress requests" in {
    // given
    val reader = InMemoryMetricReader.create()
    val backend = OpenTelemetryMetricsBackend(stubAlwaysOk, spawnNewOpenTelemetry(reader))

    // when
    (0 until 10).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/echo")))

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultRequestsInProgressCounterName).value shouldBe 0
  }

  it should "allow creating two backends" in {
    // given
    val reader = InMemoryMetricReader.create()
    val sdk = spawnNewOpenTelemetry(reader)
    val backend1 = OpenTelemetryMetricsBackend(stubAlwaysOk, sdk)
    val backend2 = OpenTelemetryMetricsBackend(stubAlwaysOk, sdk)

    // when
    backend1.send(basicRequest.get(uri"http://127.0.0.1/echo"))
    backend2.send(basicRequest.get(uri"http://127.0.0.1/echo"))

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName).value shouldBe 2
  }

  it should "use mapped request to histogram name" in {
    // given
    val customSuccessCounterName = "my_custom_counter_name"
    val reader = InMemoryMetricReader.create()
    val config = OpenTelemetryMetricsConfig(
      spawnNewOpenTelemetry(reader),
      responseToSuccessCounterMapper = _ => Some(CollectorConfig(customSuccessCounterName))
    )
    val backend = OpenTelemetryMetricsBackend(stubAlwaysOk, config)
    val requestsNumber = 5

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName) shouldBe empty
    getMetricValue(reader, customSuccessCounterName).value shouldBe 5

  }

  it should "use mapped request to change collector config" in {
    // given
    val customSuccessCounterName = "my_custom_counter_name"
    val description = "test"
    val unit = "number"
    val reader = InMemoryMetricReader.create()
    val config = OpenTelemetryMetricsConfig(
      spawnNewOpenTelemetry(reader),
      responseToSuccessCounterMapper =
        _ => Some(CollectorConfig(customSuccessCounterName, Some(description), Some(unit)))
    )
    val backend = OpenTelemetryMetricsBackend(stubAlwaysOk, config)
    val requestsNumber1 = 5

    // when
    (0 until requestsNumber1).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName) shouldBe empty
    getMetricValue(reader, customSuccessCounterName).value shouldBe 5
    val resource = getMetricResource(reader, customSuccessCounterName)
    resource.getDescription shouldBe description
    resource.getUnit shouldBe unit
  }

  it should "disable counter" in {
    // given
    val reader = InMemoryMetricReader.create()
    val config = OpenTelemetryMetricsConfig(
      spawnNewOpenTelemetry(reader),
      responseToSuccessCounterMapper = _ => None
    )
    val backend = OpenTelemetryMetricsBackend(stubAlwaysOk, config)
    val requestsNumber = 6

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName) shouldBe empty
  }

  it should "use default counter name" in {
    // given
    val backendStub1 = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backendStub2 = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val reader = InMemoryMetricReader.create()
    val sdk = spawnNewOpenTelemetry(reader)
    val backend1 = OpenTelemetryMetricsBackend(backendStub1, sdk)
    val backend2 = OpenTelemetryMetricsBackend(backendStub2, sdk)

    // when
    (0 until 10).foreach(_ => backend1.send(basicRequest.get(uri"http://127.0.0.1/foo")))
    (0 until 5).foreach(_ => backend2.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName).value shouldBe 10
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultErrorCounterName).value shouldBe 5
  }

  it should "use histogram for request and response sizes" in {
    // given
    val response = Response("Ok", StatusCode.Ok, "Ok", Seq(Header.contentLength(10)))
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespond(response)
    val reader = InMemoryMetricReader.create()
    val backend = OpenTelemetryMetricsBackend(backendStub, spawnNewOpenTelemetry(reader))

    // when
    (0 until 5).foreach(_ =>
      backend.send(
        basicRequest
          .get(uri"http://127.0.0.1/foo")
          .header(Header.contentLength(5))
      )
    )

    // then
    getHistogramValue(reader, OpenTelemetryMetricsBackend.DefaultRequestSizeHistogramName).value.getSum shouldBe 25
    getHistogramValue(reader, OpenTelemetryMetricsBackend.DefaultResponseSizeHistogramName).value.getSum shouldBe 50
  }

  it should "use histogram for request latencies" in {
    // given
    val response = Response("Ok", StatusCode.Ok, "Ok", Seq(Header.contentLength(10)))
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespond(response)
    val reader = InMemoryMetricReader.create()
    val backend = OpenTelemetryMetricsBackend(backendStub, spawnNewOpenTelemetry(reader))

    // when
    (0 until 5).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getHistogramValue(reader, OpenTelemetryMetricsBackend.DefaultLatencyHistogramName).map(_.getSum) should not be empty
  }

  it should "use error counter when http error is thrown" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondServerError()
    val reader = InMemoryMetricReader.create()
    val backend = OpenTelemetryMetricsBackend(backendStub, spawnNewOpenTelemetry(reader))

    // when
    assertThrows[HttpError[String]] {
      backend.send(
        basicRequest
          .get(uri"http://127.0.0.1/foo")
          .response(asString.getRight)
      )
    }

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName) shouldBe None
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultFailureCounterName) shouldBe None
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultErrorCounterName) shouldBe Some(1)
  }

  it should "use failure counter when other exception is thrown" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val reader = InMemoryMetricReader.create()
    val backend = OpenTelemetryMetricsBackend(backendStub, spawnNewOpenTelemetry(reader))

    // when
    assertThrows[DeserializationException[String]] {
      backend.send(
        basicRequest
          .get(uri"http://127.0.0.1/foo")
          .response(asString.map(_ => throw DeserializationException("Unknown body", new Exception("Unable to parse"))))
      )
    }

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName) shouldBe None
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultFailureCounterName) shouldBe Some(1)
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultErrorCounterName) shouldBe None
  }

  it should "use success counter on success response" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val reader = InMemoryMetricReader.create()
    val backend = OpenTelemetryMetricsBackend(backendStub, spawnNewOpenTelemetry(reader))

    // when
    backend.send(
      basicRequest
        .get(uri"http://127.0.0.1/foo")
        .response(asString.getRight)
    )

    // then
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultSuccessCounterName) shouldBe Some(1)
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultFailureCounterName) shouldBe None
    getMetricValue(reader, OpenTelemetryMetricsBackend.DefaultErrorCounterName) shouldBe None
  }

  private[this] def getMetricValue(reader: InMemoryMetricReader, name: String): Option[Long] = {
    reader
      .collectAllMetrics()
      .asScala
      .find(_.getName.equals(name))
      .map(_.getLongSumData)
      .map(_.getPoints.asScala.head.getValue)
  }

  private[this] def getHistogramValue(reader: InMemoryMetricReader, name: String): Option[HistogramPointData] =
    reader
      .collectAllMetrics()
      .asScala
      .find(_.getName.equals(name))
      .map(_.getHistogramData)
      .map(_.getPoints.asScala.head)

  private[this] def getMetricResource(reader: InMemoryMetricReader, name: String): MetricData =
    reader
      .collectAllMetrics()
      .asScala
      .find(_.getName.equals(name))
      .head

}
