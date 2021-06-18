package sttp.client3.ziotelemetry.opentelemetry

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.impl.zio.{RIOMonadAsyncError, ZioTestBase}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, Response, SttpBackend, UriContext, basicRequest}
import sttp.model.StatusCode
import zio.Task
import zio.telemetry.opentelemetry.Tracing
import scala.collection.JavaConverters._

import scala.collection.mutable

class ZioTelemetryOpenTelemetryBackendTest extends AnyFlatSpec with Matchers with BeforeAndAfter with ZioTestBase {

  private val recordedRequests = mutable.ListBuffer[Request[_, _]]()

  private val spanExporter = InMemorySpanExporter.create()

  private val mockTracer =
    SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build().get(getClass.getName)
  private val mockTracing = runtime.unsafeRun(Tracing.managed(mockTracer).useNow)

  private val backend: SttpBackend[Task, Any] =
    ZioTelemetryOpenTelemetryBackend(
      SttpBackendStub(new RIOMonadAsyncError[Any]).whenRequestMatchesPartial {
        case r if r.uri.toString.contains("echo") =>
          recordedRequests += r
          Response.ok("")
        case r if r.uri.toString.contains("error") =>
          throw new RuntimeException("something went wrong")
      },
      mockTracing
    )

  before {
    recordedRequests.clear()
    spanExporter.reset()
  }

  "ZioTelemetryOpenTelemetryBackend" should "record spans for requests" in {
    val response = runtime.unsafeRun(basicRequest.post(uri"http://stub/echo").send(backend))
    response.code shouldBe StatusCode.Ok

    val spans = spanExporter.getFinishedSpanItems.asScala
    spans should have size 1
    spans.head.getName shouldBe "POST echo"
  }

  it should "propagate span" in {
    val response = runtime.unsafeRun(basicRequest.post(uri"http://stub/echo").send(backend))
    response.code shouldBe StatusCode.Ok

    val spans = spanExporter.getFinishedSpanItems.asScala
    spans should have size 1

    val spanId = spans.head.getSpanId
    val traceId = spans.head.getTraceId
    recordedRequests(0).header("traceparent") shouldBe Some(s"00-${traceId}-${spanId}-01")
  }

}
