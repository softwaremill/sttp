package sttp.client4.prometheus

import java.lang
import java.util.concurrent.CountDownLatch
import sttp.client4._
import io.prometheus.client.CollectorRegistry
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{BeforeAndAfter, OptionValues}
import sttp.client4.testing.{BackendStub, SyncBackendStub}
import sttp.model.{Header, StatusCode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.collection.immutable.Seq
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PrometheusBackendTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfter
    with Eventually
    with OptionValues
    with IntegrationPatience {

  before {
    PrometheusBackend.clear(CollectorRegistry.defaultRegistry)
  }

  val stubAlwaysOk = SyncBackendStub.whenAnyRequest.thenRespondOk()

  "prometheus" should "use default histogram name" in {
    // given
    val backend = PrometheusBackend(stubAlwaysOk)
    val requestsNumber = 10

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(
      s"${PrometheusBackend.DefaultHistogramName}_count",
      List("method" -> "GET")
    ).value shouldBe requestsNumber
  }

  it should "allow creating two prometheus backends" in {
    // given
    val histogramName = "test_two_backends"
    val backend1 =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(
          requestToHistogramNameMapper = _ => Some(HistogramCollectorConfig(histogramName))
        )
      )
    val backend2 =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(
          requestToHistogramNameMapper = _ => Some(HistogramCollectorConfig(histogramName))
        )
      )

    // when
    basicRequest.get(uri"http://127.0.0.1/foo").send(backend1)
    basicRequest.get(uri"http://127.0.0.1/foo").send(backend1)

    // then
    getMetricValue(s"${histogramName}_count").value shouldBe 2
  }

  it should "use mapped request to histogram name" in {
    // given
    val customHistogramName = "my_custom_histogram"
    val backend =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(_ => Some(HistogramCollectorConfig(customHistogramName)))
      )
    val requestsNumber = 5

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
    getMetricValue(s"${customHistogramName}_count").value shouldBe requestsNumber
  }

  it should "use mapped request to histogram name with labels and buckets" in {
    // given
    val customHistogramName = "my_custom_histogram"
    val backend =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(r =>
          Some(
            HistogramCollectorConfig(
              customHistogramName,
              List("method" -> r.method.method),
              (1 until 10).map(i => i.toDouble).toList
            )
          )
        )
      )
    val requestsNumber1 = 5
    val requestsNumber2 = 10

    // when
    (0 until requestsNumber1).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))
    (0 until requestsNumber2).foreach(_ => basicRequest.post(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
    getMetricValue(s"${customHistogramName}_count", List("method" -> "GET")).value shouldBe requestsNumber1
    getMetricValue(s"${customHistogramName}_count", List("method" -> "POST")).value shouldBe requestsNumber2
  }

  it should "use mapped request to gauge name with labels" in {
    // given
    val customGaugeName = "my_custom_gauge"
    val backend =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(
          requestToInProgressGaugeNameMapper =
            r => Some(CollectorConfig(customGaugeName, List("method" -> r.method.method)))
        )
      )
    val requestsNumber1 = 5
    val requestsNumber2 = 10

    // when
    (0 until requestsNumber1).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))
    (0 until requestsNumber2).foreach(_ => basicRequest.post(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultRequestsInProgressGaugeName}_count") shouldBe empty
    // the gauges should be created, but set to 0
    getMetricValue(s"$customGaugeName", List("method" -> "GET")).value shouldBe 0.0
    getMetricValue(s"$customGaugeName", List("method" -> "POST")).value shouldBe 0.0
  }

  it should "disable histograms" in {
    // given
    val backend = PrometheusBackend(stubAlwaysOk, PrometheusConfig(_ => None))
    val requestsNumber = 6

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
  }

  it should "use default gauge name" in {
    // given
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = BackendStub.asynchronousFuture.whenAnyRequest.thenRespondF {
      Future {
        blocking(countDownLatch.await())
        Response.ok(Right(""))
      }
    }
    val backend = PrometheusBackend(backendStub)

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    eventually {
      getMetricValue(
        PrometheusBackend.DefaultRequestsInProgressGaugeName,
        List("method" -> "GET")
      ).value shouldBe requestsNumber
    }

    countDownLatch.countDown()
    eventually {
      getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName, List("method" -> "GET")).value shouldBe 0
    }
  }

  it should "use mapped request to gauge name" in {
    // given
    val customGaugeName = "my_custom_gauge"
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = BackendStub.asynchronousFuture.whenAnyRequest.thenRespondF {
      Future {
        blocking(countDownLatch.await())
        Response.ok(Right(""))
      }
    }
    val backend =
      PrometheusBackend(
        backendStub,
        PrometheusConfig(
          requestToInProgressGaugeNameMapper = _ => Some(CollectorConfig(customGaugeName))
        )
      )

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    eventually {
      getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty
      getMetricValue(customGaugeName).value shouldBe requestsNumber
    }

    countDownLatch.countDown()
    eventually {
      getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty
      getMetricValue(customGaugeName).value shouldBe 0
    }
  }

  it should "disable gauge" in {
    // given
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = BackendStub.asynchronousFuture.whenAnyRequest.thenRespondF {
      Future {
        blocking(countDownLatch.await())
        Response.ok(Right(""))
      }
    }
    val backend =
      PrometheusBackend(backendStub, PrometheusConfig(requestToInProgressGaugeNameMapper = _ => None))

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty

    countDownLatch.countDown()
    eventually {
      getMetricValue(
        s"${PrometheusBackend.DefaultHistogramName}_count",
        List("method" -> "GET")
      ).value shouldBe requestsNumber
      getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty
    }
  }

  it should "use default counter name" in {
    // given
    val backendStub1 = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backendStub2 = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val backend1 = PrometheusBackend(backendStub1)
    val backend2 = PrometheusBackend(backendStub2)

    // when
    (0 until 10).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend1))
    (0 until 5).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend2))

    // then
    getMetricValue(
      PrometheusBackend.DefaultSuccessCounterName + "_total",
      List("method" -> "GET", "status" -> "2xx")
    ).value shouldBe 10
    getMetricValue(
      PrometheusBackend.DefaultErrorCounterName + "_total",
      List("method" -> "GET", "status" -> "4xx")
    ).value shouldBe 5
  }

  it should "not override user-supplied 'method' and 'status' labels" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend(
      backendStub,
      PrometheusConfig(
        responseToSuccessCounterMapper = (_, _) =>
          Some(CollectorConfig(PrometheusBackend.DefaultSuccessCounterName, List(("method", "foo"), ("status", "bar"))))
      )
    )

    // when
    (0 until 10).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(
      PrometheusBackend.DefaultSuccessCounterName + "_total",
      List("method" -> "foo", "status" -> "bar")
    ).value shouldBe 10
  }

  it should "use default summary name" in {
    // given
    val response = Response("Ok", StatusCode.Ok, "Ok", Seq(Header.contentLength(10)))
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespond(response)
    val backend = PrometheusBackend(backendStub)

    // when
    (0 until 5).foreach(_ =>
      basicRequest
        .get(uri"http://127.0.0.1/foo")
        .header(Header.contentLength(5))
        .send(backend)
    )

    // then
    getMetricValue(PrometheusBackend.DefaultRequestSizeName + "_count", List("method" -> "GET")).value shouldBe 5
    getMetricValue(PrometheusBackend.DefaultRequestSizeName + "_sum", List("method" -> "GET")).value shouldBe 25
    getMetricValue(
      PrometheusBackend.DefaultResponseSizeName + "_count",
      List("method" -> "GET", "status" -> "2xx")
    ).value shouldBe 5
    getMetricValue(
      PrometheusBackend.DefaultResponseSizeName + "_sum",
      List("method" -> "GET", "status" -> "2xx")
    ).value shouldBe 50
  }

  it should "use error counter when http error is thrown" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondServerError()
    val backend = PrometheusBackend(backendStub)

    // when
    assertThrows[SttpClientException] {
      basicRequest
        .get(uri"http://127.0.0.1/foo")
        .response(asString.getRight)
        .send(backend)
    }

    // then
    getMetricValue(
      PrometheusBackend.DefaultSuccessCounterName + "_total",
      List("method" -> "GET", "status" -> "2xx")
    ) shouldBe None
    getMetricValue(PrometheusBackend.DefaultFailureCounterName + "_total", List("method" -> "GET")) shouldBe None
    getMetricValue(
      PrometheusBackend.DefaultErrorCounterName + "_total",
      List("method" -> "GET", "status" -> "5xx")
    ) shouldBe Some(1)
  }

  it should "use failure counter when other exception is thrown" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend(backendStub)

    // when
    assertThrows[SttpClientException] {
      basicRequest
        .get(uri"http://127.0.0.1/foo")
        .response(asString.map(_ => throw DeserializationException("Unknown body", new Exception("Unable to parse"))))
        .send(backend)
    }

    // then
    getMetricValue(
      PrometheusBackend.DefaultSuccessCounterName + "_total",
      List("method" -> "GET", "status" -> "2xx")
    ) shouldBe None
    getMetricValue(PrometheusBackend.DefaultFailureCounterName + "_total", List("method" -> "GET")) shouldBe Some(1)
    getMetricValue(
      PrometheusBackend.DefaultErrorCounterName + "_total",
      List("method" -> "GET", "status" -> "5xx")
    ) shouldBe None
  }

  it should "use success counter on success response" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend(backendStub)

    // when
    basicRequest
      .get(uri"http://127.0.0.1/foo")
      .response(asString.getRight)
      .send(backend)

    // then
    getMetricValue(
      PrometheusBackend.DefaultSuccessCounterName + "_total",
      List("method" -> "GET", "status" -> "2xx")
    ) shouldBe Some(1)
    getMetricValue(PrometheusBackend.DefaultFailureCounterName + "_total", List("method" -> "GET")) shouldBe None
    getMetricValue(
      PrometheusBackend.DefaultErrorCounterName + "_total",
      List("method" -> "GET", "status" -> "5xx")
    ) shouldBe None
  }

  it should "report correct host when it is extracted from the response" in {
    // given
    val backendStub =
      SyncBackendStub.whenAnyRequest.thenRespondF(_ => throw new HttpError("boom", StatusCode.BadRequest))

    import sttp.client4.prometheus.PrometheusBackend.{DefaultFailureCounterName, addMethodLabel}

    val HostLabel = "Host"
    def addHostLabel[T <: BaseCollectorConfig](config: T, resp: Response[_]): config.T = {
      val hostLabel: Option[(String, String)] =
        if (config.labels.map(_._1.toLowerCase).contains(HostLabel)) None
        else Some((HostLabel, resp.request.uri.host.getOrElse("-")))

      config.addLabels(hostLabel.toList)
    }

    val backend = PrometheusBackend(
      backendStub,
      PrometheusConfig.Default.copy(
        responseToErrorCounterMapper = (req: GenericRequest[_, _], resp: Response[_]) =>
          Some(addHostLabel(addMethodLabel(CollectorConfig(DefaultFailureCounterName), req), resp))
      )
    )

    // when
    assertThrows[SttpClientException](basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue(
      PrometheusBackend.DefaultFailureCounterName + "_total",
      List("method" -> "GET", HostLabel -> "127.0.0.1")
    ) shouldBe Some(1)
  }

  private[this] def getMetricValue(name: String): Option[lang.Double] =
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name))

  private[this] def getMetricValue(name: String, labels: List[(String, String)]): Option[lang.Double] =
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name, labels.map(_._1).toArray, labels.map(_._2).toArray))
}
