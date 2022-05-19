package sttp.client3.opentelemetry

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, Request, Response, SttpBackend, UriContext, basicRequest}
import sttp.model.StatusCode

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class OpenTelemetryTracingBackendTest extends AnyFlatSpec with Matchers with BeforeAndAfter {

  private val recordedRequests = mutable.ListBuffer[Request[_, _]]()

  private val spanExporter = InMemorySpanExporter.create()

  private val mockTracer: SdkTracerProvider =
    SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build()

  private val mockOpenTelemetry = OpenTelemetrySdk
    .builder()
    .setTracerProvider(mockTracer)
    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
    .buildAndRegisterGlobal()

  private val backend: SttpBackend[Identity, Any] =
    OpenTelemetryTracingBackend(
      SttpBackendStub.apply(IdMonad).whenRequestMatchesPartial {
        case r if r.uri.toString.contains("echo") =>
          recordedRequests += r
          Response.ok("")
        case r if r.uri.toString.contains("error") =>
          throw new RuntimeException("something went wrong")
      },
      mockOpenTelemetry
    )

  before {
    recordedRequests.clear()
    spanExporter.reset()
  }

  "OpenTelemetryTracingBackend" should "record spans for requests" in {
    val response = basicRequest.post(uri"http://stub/echo").send(backend)
    response.code shouldBe StatusCode.Ok

    val spans = spanExporter.getFinishedSpanItems.asScala
    spans should have size 1
    spans.head.getName shouldBe "HTTP POST"
  }

  it should "propagate span" in {
    val response = basicRequest.post(uri"http://stub/echo").send(backend)
    response.code shouldBe StatusCode.Ok

    val spans = spanExporter.getFinishedSpanItems.asScala
    spans should have size 1

    val spanId = spans.head.getSpanId
    val traceId = spans.head.getTraceId
    recordedRequests(0).header("traceparent") shouldBe Some(s"00-${traceId}-${spanId}-01")
  }

  it should "set span status in case of error" in {
    Try(basicRequest.post(uri"http://stub/error").send(backend))

    val spans = spanExporter.getFinishedSpanItems.asScala
    spans should have size 1

    spans.head.getStatus.getStatusCode shouldBe io.opentelemetry.api.trace.StatusCode.ERROR
  }

}
