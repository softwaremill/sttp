package sttp.client.opentracing

import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import sttp.client.monad.IdMonad
import sttp.client.testing.SttpBackendStub
import sttp.client.{Identity, NothingT, SttpBackend, _}
import sttp.model.{Method, StatusCode}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class OpenTracingBackendTest extends FlatSpec with Matchers with BeforeAndAfter {

  private val recordedRequests = mutable.ListBuffer[Request[_, _]]()
  private val tracer = new MockTracer()
  private implicit val backend: SttpBackend[Identity, Nothing, NothingT] =
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
    val response = basicRequest.post(uri"http://stub/echo").send()
    response.code shouldBe StatusCode.Ok

    val spans = tracer.finishedSpans()
    spans should have size 1
    val spanId = spans.asScala.head.context().spanId()
    val traceId = spans.asScala.head.context().traceId()
    recordedRequests(0).headers.collectFirst { case h if h.name == "spanid"  => h.value } shouldBe Some(spanId.toString)
    recordedRequests(0).headers.collectFirst { case h if h.name == "traceid" => h.value } shouldBe Some(
      traceId.toString
    )
  }

  it should "make child of current span" in {
    val operationName = "my-custom-ops-id"
    val span = tracer.buildSpan(operationName).start()
    tracer.activateSpan(span)
    val response = basicRequest.post(uri"http://stub/echo").send()
    response.code shouldBe StatusCode.Ok
    span.finish()

    val spans = tracer.finishedSpans()
    spans should have size 2
    val spanId = span.context().spanId()
    spans.asScala(0).parentId() shouldBe spanId
    spans.asScala(1).context().spanId() shouldBe spanId
    spans.asScala(1).operationName() shouldBe operationName
  }

  it should "propagate additional metadata" in {
    val span = tracer.buildSpan("my-custom-ops-id").start()
    span.setBaggageItem("baggage1", "hello")
    tracer.activateSpan(span)
    val response = basicRequest.post(uri"http://stub/echo").send()
    response.code shouldBe StatusCode.Ok
    span.finish()

    recordedRequests.head.headers.collectFirst { case h if h.name == "baggage-baggage1" => h.value } shouldBe Some(
      "hello"
    )
  }

  it should "add status code when response is not error" in {
    val response = basicRequest.post(uri"http://stub/echo").send()
    response.code shouldBe StatusCode.Ok

    tracer.finishedSpans().asScala.head.tags().asScala(Tags.HTTP_STATUS.getKey) shouldBe StatusCode.Ok.code
  }

  it should "add logs if response is error" in {
    Try(basicRequest.post(uri"http://stub/error").send())

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
    basicRequest.post(url).send()
    val tags = tracer.finishedSpans().asScala.head.tags().asScala
    tags(Tags.HTTP_METHOD.getKey) shouldBe Method.POST.method
    tags(Tags.HTTP_URL.getKey) shouldBe url.toJavaUri.toString
    tags(Tags.SPAN_KIND.getKey) shouldBe Tags.SPAN_KIND_CLIENT
    tags(Tags.COMPONENT.getKey) shouldBe "sttp2-client"
  }

  it should "be able to adjust span" in {
    import OpenTracingBackend._

    val url = uri"http://stub/echo"
    basicRequest
      .post(url)
      .tagWithTransformSpan(_.setTag("custom-tag", "custom-value").setOperationName("new-name").log("my-event"))
      .send()

    val span = tracer.finishedSpans().asScala.head
    span.tags().get("custom-tag") shouldBe "custom-value"
    span.operationName() shouldBe "new-name"
    span.logEntries().get(0).fields().asScala shouldBe Map("event" -> "my-event")
  }
}
