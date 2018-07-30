package com.softwaremill.sttp.prometheus

import java.lang
import java.util.concurrent.CountDownLatch

import com.softwaremill.sttp.testing.SttpBackendStub
import com.softwaremill.sttp._
import io.prometheus.client.CollectorRegistry
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers, OptionValues}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrometheusBackendTest extends FlatSpec with Matchers with BeforeAndAfter with Eventually with OptionValues {

  before {
    PrometheusBackend.clear(CollectorRegistry.defaultRegistry)
  }

  it should "use default histogram name" in {
    // given
    val backendStub = SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend[Id, Nothing](backendStub)
    val requestsNumber = 10

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricVale(s"${PrometheusBackend.DefaultHistogramName}_count").value shouldBe requestsNumber
  }

  it should "allow creating two prometheus backends" in {
    // given
    val backendStub = SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk()
    val histogramName = "test_two_backends"
    val backend1 = PrometheusBackend[Id, Nothing](backendStub, requestToHistogramNameMapper = _ => Some(histogramName))
    val backend2 = PrometheusBackend[Id, Nothing](backendStub, requestToHistogramNameMapper = _ => Some(histogramName))

    // when
    backend1.send(sttp.get(uri"http://127.0.0.1/foo"))
    backend2.send(sttp.get(uri"http://127.0.0.1/foo"))

    // then
    getMetricVale(s"${histogramName}_count").value shouldBe 2
  }

  it should "use mapped request to histogram name" in {
    // given
    val customHistogramName = "my_custom_histogram"
    val backend =
      PrometheusBackend[Id, Nothing](SttpBackendStub.synchronous, _ => Some(customHistogramName))
    val requestsNumber = 5

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricVale(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
    getMetricVale(s"${customHistogramName}_count").value shouldBe requestsNumber
  }

  it should "disable histograms" in {
    // given
    val backend =
      PrometheusBackend[Id, Nothing](SttpBackendStub.synchronous, _ => None)
    val requestsNumber = 6

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricVale(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe empty
  }

  it should "use default gauge name" in {
    // given
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = SttpBackendStub.asynchronousFuture.whenAnyRequest.thenRespondWrapped {
      Future {
        countDownLatch.await()
        Response(Right(""), 200, "", Nil, Nil)
      }
    }
    val backend = PrometheusBackend[Future, Nothing](backendStub)

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    eventually {
      getMetricVale(PrometheusBackend.DefaultRequestsInProgressGaugeName).value shouldBe requestsNumber
    }

    countDownLatch.countDown()
    eventually {
      getMetricVale(PrometheusBackend.DefaultRequestsInProgressGaugeName).value shouldBe 0
    }
  }

  it should "use mapped request to gauge name" in {
    // given
    val customGaugeName = "my_custom_gauge"
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = SttpBackendStub.asynchronousFuture.whenAnyRequest.thenRespondWrapped {
      Future {
        countDownLatch.await()
        Response(Right(""), 200, "", Nil, Nil)
      }
    }
    val backend =
      PrometheusBackend[Future, Nothing](backendStub, requestToInProgressGaugeNameMapper = _ => Some(customGaugeName))

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    eventually {
      getMetricVale(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty
      getMetricVale(customGaugeName).value shouldBe requestsNumber
    }

    countDownLatch.countDown()
    eventually {
      getMetricVale(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty
      getMetricVale(customGaugeName).value shouldBe 0
    }
  }

  it should "disable gauge" in {
    // given
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = SttpBackendStub.asynchronousFuture.whenAnyRequest.thenRespondWrapped {
      Future {
        countDownLatch.await()
        Response(Right(""), 200, "", Nil, Nil)
      }
    }
    val backend = PrometheusBackend[Future, Nothing](backendStub, requestToInProgressGaugeNameMapper = _ => None)

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricVale(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty

    countDownLatch.countDown()
    eventually {
      getMetricVale(s"${PrometheusBackend.DefaultHistogramName}_count").value shouldBe requestsNumber
      getMetricVale(PrometheusBackend.DefaultRequestsInProgressGaugeName) shouldBe empty
    }
  }

  it should "use default counter name" in {
    // given
    val backendStub1 = SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk()
    val backendStub2 = SttpBackendStub.synchronous.whenAnyRequest.thenRespondNotFound()
    val backend1 = PrometheusBackend[Id, Nothing](backendStub1)
    val backend2 = PrometheusBackend[Id, Nothing](backendStub2)

    // when
    (0 until 10).foreach(_ => backend1.send(sttp.get(uri"http://127.0.0.1/foo")))
    (0 until 5).foreach(_ => backend2.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    getMetricVale(PrometheusBackend.DefaultSuccessCounterName).value shouldBe 10
    getMetricVale(PrometheusBackend.DefaultErrorCounterName).value shouldBe 5
  }

  private[this] def getMetricVale(name: String): Option[lang.Double] =
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name))

}
