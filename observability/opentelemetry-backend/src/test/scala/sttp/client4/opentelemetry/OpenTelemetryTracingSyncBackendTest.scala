package sttp.client4.opentelemetry

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import sttp.client4.testing.SyncBackendStub
import sttp.client4._
import io.opentelemetry.sdk.OpenTelemetrySdk
import scala.jdk.CollectionConverters._
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.ErrorAttributes

class OpenTelemetryTracingSyncBackendTest extends AnyFlatSpec with Matchers {
  it should "capture successful spans" in {
    // given
    val testExporter = InMemorySpanExporter.create()
    val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build();
    val otel = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()

    val stubBackend = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val wrappedBackend = OpenTelemetryTracingSyncBackend(stubBackend, OpenTelemetryTracingSyncConfig(otel))

    // when
    basicRequest.get(uri"http://test.com/foo").send(wrappedBackend)

    // then
    val spanItems = testExporter.getFinishedSpanItems().asScala
    spanItems should have size 1

    val span = spanItems.head
    val attributes = span.getAttributes().asMap().asScala
    attributes(UrlAttributes.URL_FULL) shouldBe "http://test.com/foo"
    attributes(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) shouldBe 200
  }

  it should "capture spans which end in an exception" in {
    // given
    val testExporter = InMemorySpanExporter.create()
    val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build();
    val otel = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()

    val stubBackend = SyncBackendStub.whenAnyRequest.thenRespond(throw new RuntimeException("test"))
    val wrappedBackend = OpenTelemetryTracingSyncBackend(stubBackend, OpenTelemetryTracingSyncConfig(otel))

    // when
    intercept[RuntimeException] {
      basicRequest.get(uri"http://test.com/foo").send(wrappedBackend)
    }

    // then
    val spanItems = testExporter.getFinishedSpanItems().asScala
    spanItems should have size 1

    val span = spanItems.head
    val attributes = span.getAttributes().asMap().asScala
    attributes(UrlAttributes.URL_FULL) shouldBe "http://test.com/foo"
    attributes(ErrorAttributes.ERROR_TYPE) shouldBe "RuntimeException"
  }
}
