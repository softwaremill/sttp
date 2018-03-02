package com.softwaremill.sttp.prometheus

import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, sttp}
import io.prometheus.client.CollectorRegistry
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import com.softwaremill.sttp._
import com.softwaremill.sttp.testing.SttpBackendStub

class PrometheusBackendTest extends FlatSpec with Matchers with BeforeAndAfter {

  it should "use default histogram name" in {
    // given
    val backendStub = SttpBackendStub(HttpURLConnectionBackend()).whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend[Id, Nothing](backendStub)
    val requestsNumber = 10

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    val result = CollectorRegistry.defaultRegistry.getSampleValue(s"${PrometheusBackend.DefaultHistogramName}_count")
    result shouldBe requestsNumber
  }

  it should "use mapped request to histogram name" in {
    // given
    val customHistogramName = "my-custom-histogram"
    val backend =
      PrometheusBackend[Id, Nothing](SttpBackendStub(HttpURLConnectionBackend()), Some(_ => customHistogramName))
    val requestsNumber = 5

    // when
    (0 until requestsNumber).foreach(_ => backend.send(sttp.get(uri"http://127.0.0.1/foo")))

    // then
    CollectorRegistry.defaultRegistry.getSampleValue(s"${PrometheusBackend.DefaultHistogramName}_count") shouldBe null
    CollectorRegistry.defaultRegistry.getSampleValue(s"${customHistogramName}_count") shouldBe requestsNumber
  }
}
