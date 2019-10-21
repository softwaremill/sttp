package sttp.client.prometheus

import java.lang
import java.util.concurrent.CountDownLatch

import sttp.client.testing.SttpBackendStub
import sttp.client._
import io.prometheus.client.CollectorRegistry
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers, OptionValues}
import sttp.client.testing.SttpBackendStub
import sttp.model.StatusCode

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.blocking

class PrometheusBackendTest
    extends FlatSpec
    with Matchers
    with BeforeAndAfter
    with Eventually
    with OptionValues
    with IntegrationPatience {

  before {
    PrometheusBackend.clear(CollectorRegistry.defaultRegistry)
  }

  it should "use default histogram name" in {
    // given
    val backendStub = SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend[Identity, Nothing](backendStub)
    val requestsNumber = 10

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count").value shouldBe requestsNumber
  }

  it should "allow creating two prometheus backends" in {
    // given
    val backendStub = SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk()
    val histogramName = "test_two_backends"
    val backend1 =
      PrometheusBackend[Identity, Nothing](
        backendStub,
        requestToHistogramNameMapper = _ => Some(CollectorNameWithLabels(histogramName))
      )
    val backend2 =
      PrometheusBackend[Identity, Nothing](
        backendStub,
        requestToHistogramNameMapper = _ => Some(CollectorNameWithLabels(histogramName))
      )

    // when
    backend1.send(basicRequest.get(uri"http://127.0.0.1/foo"))
    backend2.send(basicRequest.get(uri"http://127.0.0.1/foo"))

    // then
    getMetricValue(s"${histogramName}_count").value shouldBe 2
  }

  it should "use mapped request to histogram name" in {
    // given
    val customHistogramName = "my_custom_histogram"
    val backend =
      PrometheusBackend[Identity, Nothing](
        SttpBackendStub.synchronous,
        _ => Some(CollectorNameWithLabels(customHistogramName))
      )
    val requestsNumber = 5

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
    getMetricValue(s"${customHistogramName}_count").value shouldBe requestsNumber
  }

  it should "use mapped request to histogram name with labels" in {
    // given
    val customHistogramName = "my_custom_histogram"
    val backend =
      PrometheusBackend[Identity, Nothing](
        SttpBackendStub.synchronous,
        r => Some(CollectorNameWithLabels(customHistogramName, List("method" -> r.method.method)))
      )
    val requestsNumber1 = 5
    val requestsNumber2 = 10

    // when
    (0 until requestsNumber1).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))
    (0 until requestsNumber2).foreach(_ => backend.send(basicRequest.post(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
    getMetricValue(s"${customHistogramName}_count", List("method" -> "GET")).value shouldBe requestsNumber1
    getMetricValue(s"${customHistogramName}_count", List("method" -> "POST")).value shouldBe requestsNumber2
  }

  it should "disable histograms" in {
    // given
    val backend =
      PrometheusBackend[Identity, Nothing](SttpBackendStub.synchronous, _ => None)
    val requestsNumber = 6

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
  }

  it should "use default gauge name" in {
    // given
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = SttpBackendStub.asynchronousFuture.whenAnyRequest.thenRespondWrapped {
      Future {
        blocking(countDownLatch.await())
        Response(Right(""), StatusCode.Ok, "", Nil, Nil)
      }
    }
    val backend = PrometheusBackend[Future, Nothing](backendStub)

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    eventually {
      getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName).value shouldBe requestsNumber
    }

    countDownLatch.countDown()
    eventually {
      getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName).value shouldBe 0
    }
  }

  it should "use mapped request to gauge name" in {
    // given
    val customGaugeName = "my_custom_gauge"
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = SttpBackendStub.asynchronousFuture.whenAnyRequest.thenRespondWrapped {
      Future {
        blocking(countDownLatch.await())
        Response(Right(""), StatusCode.Ok, "", Nil, Nil)
      }
    }
    val backend =
      PrometheusBackend[Future, Nothing](
        backendStub,
        requestToInProgressGaugeNameMapper = _ => Some(CollectorNameWithLabels(customGaugeName))
      )

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

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
    val backendStub = SttpBackendStub.asynchronousFuture.whenAnyRequest.thenRespondWrapped {
      Future {
        blocking(countDownLatch.await())
        Response(Right(""), StatusCode.Ok, "", Nil, Nil)
      }
    }
    val backend = PrometheusBackend[Future, Nothing](backendStub, requestToInProgressGaugeNameMapper = _ => None)

    // when
    (0 until requestsNumber).foreach(_ => backend.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty

    countDownLatch.countDown()
    eventually {
      getMetricValue(s"${PrometheusBackend.DefaultHistogramName}_count").value shouldBe requestsNumber
      getMetricValue(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty
    }
  }

  it should "use default counter name" in {
    // given
    val backendStub1 = SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk()
    val backendStub2 = SttpBackendStub.synchronous.whenAnyRequest.thenRespondNotFound()
    val backend1 = PrometheusBackend[Identity, Nothing](backendStub1)
    val backend2 = PrometheusBackend[Identity, Nothing](backendStub2)

    // when
    (0 until 10).foreach(_ => backend1.send(basicRequest.get(uri"http://127.0.0.1/foo")))
    (0 until 5).foreach(_ => backend2.send(basicRequest.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricValue(PrometheusBackend.DefaultSuccessCounterName).value shouldBe 10
    getMetricValue(PrometheusBackend.DefaultErrorCounterName).value shouldBe 5
  }

  private[this] def getMetricValue(name: String): Option[lang.Double] =
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name))

  private[this] def getMetricValue(name: String, labels: List[(String, String)]): Option[lang.Double] =
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name, labels.map(_._1).toArray, labels.map(_._2).toArray))

}
