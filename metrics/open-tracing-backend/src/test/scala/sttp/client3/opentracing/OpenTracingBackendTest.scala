package sttp.client3.opentracing

import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, SttpBackend, _}
import sttp.client3.opentracing.OpenTracingBackend._
import sttp.model.{Method, StatusCode}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class OpenTracingBackendTest extends AnyFlatSpec with Matchers with BeforeAndAfter {

  private val recordedRequests = mutable.ListBuffer[Request[_, _]]()
  private val tracer = new MockTracer()
  private val backend: SttpBackend[Identity, Any] =
    OpenTracingBackend[Identity, Nothing](
      SttpBackendStub.apply(IdMonad).whenRequestMatchesPartial {
        case r if r.uri.toString.contains("echo") =>
          recordedRequests += r
          Response.ok("")
        case r if r.uri.toString.contains("error") =>
          throw new RuntimeException("something went wrong")
      },
      tracer
    )

  before {
    recordedRequests.clear()
    tracer.reset()
  }

  "OpenTracingBackendTest" should "propagate span" in {
    val response = basicRequest.post(uri"http://stub/echo").send(backend)
    response.code shouldBe StatusCode.Ok

    val spans = tracer.finishedSpans()
    spans should have size 1
    val spanId = spans.asScala.head.context().spanId()
    val traceId = spans.asScala.head.context().traceId()
    recordedRequests(0).headers.collectFirst { case h if h.name == "spanid" => h.value } shouldBe Some(spanId.toString)
    recordedRequests(0).headers.collectFirst { case h if h.name == "traceid" => h.value } shouldBe Some(
      traceId.toString
    )
  }

  it should "make child of current span" in {
    val operationName = "my-custom-ops-id"
    val span = tracer.buildSpan(operationName).start()
    tracer.activateSpan(span)
    val response = basicRequest.post(uri"http://stub/echo").send(backend)
    response.code shouldBe StatusCode.Ok
    span.finish()

    val spans = tracer.finishedSpans()
    spans should have size 2
    val spanId = span.context().spanId()
    spans.asScala(0).parentId() shouldBe spanId
    spans.asScala(1).context().spanId() shouldBe spanId
    spans.asScala(1).operationName() shouldBe operationName
  }

  it should "make child of given span if defined" in {
    val activeSpan = tracer.buildSpan("active-op").start()
    tracer.activateSpan(activeSpan)
    val parentSpan = tracer.buildSpan("parent-op").start()
    val response = basicRequest
      .post(uri"http://stub/echo")
      .tagWithOperationId("overridden-op")
      .setOpenTracingParentSpan(parentSpan)
      .send(backend)
    response.code shouldBe StatusCode.Ok
    parentSpan.finish()

    val spans = tracer.finishedSpans().asScala

    spans should have size 2
    spans(0).parentId shouldBe spans(1).context.spanId
    spans(1).operationName shouldBe "parent-op"
    spans(0).operationName shouldBe "overridden-op"
  }

  it should "make child of given span context if defined" in {
    val activeSpan = tracer.buildSpan("active-op").start()
    tracer.activateSpan(activeSpan)
    val response = basicRequest
      .post(uri"http://stub/echo")
      .tagWithOperationId("overridden-op")
      .setOpenTracingParentSpanContext(activeSpan.context())
      .send(backend)
    response.code shouldBe StatusCode.Ok

    val spans = tracer.finishedSpans().asScala

    spans should have size 1
    spans(0).parentId() shouldBe activeSpan.context().spanId()
    spans(0).operationName shouldBe "overridden-op"
  }

  it should "propagate additional metadata" in {
    val span = tracer.buildSpan("my-custom-ops-id").start()
    span.setBaggageItem("baggage1", "hello")
    tracer.activateSpan(span)
    val response = basicRequest.post(uri"http://stub/echo").send(backend)
    response.code shouldBe StatusCode.Ok
    span.finish()

    recordedRequests.head.headers.collectFirst { case h if h.name == "baggage-baggage1" => h.value } shouldBe Some(
      "hello"
    )
  }

  it should "add status code when response is not error" in {
    val response = basicRequest.post(uri"http://stub/echo").send(backend)
    response.code shouldBe StatusCode.Ok

    tracer
      .finishedSpans()
      .asScala
      .head
      .tags()
      .asScala(Tags.HTTP_STATUS.getKey)
      .asInstanceOf[Int] shouldBe StatusCode.Ok.code
  }

  it should "add logs if response is error" in {
    Try(basicRequest.post(uri"http://stub/error").send(backend))

    val finishedSpan = tracer
      .finishedSpans()
      .asScala
      .head
    finishedSpan
      .logEntries()
      .asScala
      .head
      .fields()
      .asScala("error.object")
      .asInstanceOf[RuntimeException]
      .getMessage shouldBe "something went wrong"
    finishedSpan.tags().asScala(Tags.ERROR.getKey) shouldBe java.lang.Boolean.TRUE
  }

  it should "add standard tags during http call" in {
    val url = uri"http://stub/echo"
    basicRequest.post(url).send(backend)
    val tags = tracer.finishedSpans().asScala.head.tags().asScala
    tags(Tags.HTTP_METHOD.getKey) shouldBe Method.POST.method
    tags(Tags.HTTP_URL.getKey) shouldBe url.toJavaUri.toString
    tags(Tags.SPAN_KIND.getKey) shouldBe Tags.SPAN_KIND_CLIENT
    tags(Tags.COMPONENT.getKey) shouldBe "sttp3-client"
  }

  it should "be able to adjust span" in {
    import OpenTracingBackend._

    val url = uri"http://stub/echo"
    basicRequest
      .post(url)
      .tagWithTransformSpan(_.setTag("custom-tag", "custom-value").setOperationName("new-name").log("my-event"))
      .send(backend)

    val span = tracer.finishedSpans().asScala.head
    span.tags().get("custom-tag") shouldBe "custom-value"
    span.operationName() shouldBe "new-name"
    span.logEntries().get(0).fields().asScala shouldBe Map("event" -> "my-event")
  }
}
