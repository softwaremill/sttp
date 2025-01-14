package sttp.client4.prometheus

import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot.HistogramDataPointSnapshot
import io.prometheus.metrics.model.snapshots.SummarySnapshot.SummaryDataPointSnapshot
import io.prometheus.metrics.model.snapshots.{DataPointSnapshot, Labels}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, OptionValues}
import sttp.client4._
import sttp.client4.testing.{BackendStub, ResponseStub, SyncBackendStub}
import sttp.model.{Header, StatusCode}

import java.util.concurrent.CountDownLatch
import java.util.stream.Collectors
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{blocking, Future}
import scala.collection.immutable.Seq

class PrometheusBackendTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfter
    with Eventually
    with OptionValues
    with IntegrationPatience {

  before {
    PrometheusBackend.clear(PrometheusRegistry.defaultRegistry)
  }

  val stubAlwaysOk = SyncBackendStub.whenAnyRequest.thenRespondOk()

  "prometheus" should "use default histogram name" in {
    // given
    val backend = PrometheusBackend(stubAlwaysOk)
    val requestsNumber = 10

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue[HistogramDataPointSnapshot](
      s"${PrometheusBackend.DefaultHistogramName}",
      List("method" -> "GET")
    ).map(_.getCount).value shouldBe requestsNumber
  }

  it should "allow creating two prometheus backends" in {
    // given
    val histogramName = "test_two_backends_seconds"
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
    getMetricSnapshot[HistogramDataPointSnapshot](s"$histogramName").map(_.getCount).value shouldBe 2
  }

  it should "use mapped request to histogram name" in {
    // given
    val customHistogramName = "my_custom_histogram_seconds"
    val backend =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(_ => Some(HistogramCollectorConfig(customHistogramName)))
      )
    val requestsNumber = 5

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricSnapshot[HistogramDataPointSnapshot](s"${PrometheusBackend.DefaultHistogramName}") shouldBe empty
    getMetricSnapshot[HistogramDataPointSnapshot](s"$customHistogramName").map(_.getCount).value shouldBe requestsNumber
  }

  it should "use mapped request to histogram name with labels and buckets" in {
    // given
    val customHistogramName = "my_custom_histogram_seconds"
    val backend =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(r =>
          Some(
            HistogramCollectorConfig(
              customHistogramName,
              labels = List("method" -> r.method.method),
              buckets = (1 until 10).map(i => i.toDouble).toList
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
    getMetricSnapshot(s"${PrometheusBackend.DefaultHistogramName}") shouldBe empty
    getMetricValue[HistogramDataPointSnapshot](s"$customHistogramName", List("method" -> "GET"))
      .map(_.getCount)
      .value shouldBe requestsNumber1
    getMetricValue[HistogramDataPointSnapshot](s"$customHistogramName", List("method" -> "POST"))
      .map(_.getCount)
      .value shouldBe requestsNumber2
  }

  it should "use mapped request to gauge name with labels" in {
    // given
    val customGaugeName = "my_custom_gauge"
    val backend =
      PrometheusBackend(
        stubAlwaysOk,
        PrometheusConfig(
          requestToInProgressGaugeNameMapper =
            r => Some(CollectorConfig(collectorName = customGaugeName, labels = List("method" -> r.method.method)))
        )
      )
    val requestsNumber1 = 5
    val requestsNumber2 = 10

    // when
    (0 until requestsNumber1).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))
    (0 until requestsNumber2).foreach(_ => basicRequest.post(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricSnapshot[GaugeDataPointSnapshot](s"${PrometheusBackend.DefaultRequestsActiveGaugeName}") shouldBe empty
    // the gauges should be created, but set to 0
    getMetricValue[GaugeDataPointSnapshot](s"$customGaugeName", List("method" -> "GET"))
      .map(_.getValue)
      .value shouldBe 0.0
    getMetricValue[GaugeDataPointSnapshot](s"$customGaugeName", List("method" -> "POST"))
      .map(_.getValue)
      .value shouldBe 0.0
  }

  it should "disable histograms" in {
    // given
    val backend = PrometheusBackend(stubAlwaysOk, PrometheusConfig(_ => None))
    val requestsNumber = 6

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricSnapshot(s"${PrometheusBackend.DefaultHistogramName}") shouldBe empty
  }

  it should "use default gauge name" in {
    // given
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = BackendStub.asynchronousFuture.whenAnyRequest.thenRespondF {
      Future {
        blocking(countDownLatch.await())
        ResponseStub.ok(Right(""))
      }
    }
    val backend = PrometheusBackend(backendStub)

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    eventually {
      getMetricValue[GaugeDataPointSnapshot](
        PrometheusBackend.DefaultRequestsActiveGaugeName,
        List("method" -> "GET")
      ).map(_.getValue).value shouldBe requestsNumber
    }

    countDownLatch.countDown()
    eventually {
      getMetricValue[GaugeDataPointSnapshot](PrometheusBackend.DefaultRequestsActiveGaugeName, List("method" -> "GET"))
        .map(_.getValue)
        .value shouldBe 0
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
        ResponseStub.ok(Right(""))
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
      getMetricSnapshot(PrometheusBackend.DefaultRequestsActiveGaugeName) shouldBe empty
      getMetricSnapshot[GaugeDataPointSnapshot](customGaugeName).map(_.getValue).value shouldBe requestsNumber
    }

    countDownLatch.countDown()
    eventually {
      getMetricSnapshot(PrometheusBackend.DefaultRequestsActiveGaugeName) shouldBe empty
      getMetricSnapshot[GaugeDataPointSnapshot](customGaugeName).map(_.getValue).value shouldBe 0
    }
  }

  it should "disable gauge" in {
    // given
    val requestsNumber = 10
    val countDownLatch = new CountDownLatch(1)
    val backendStub = BackendStub.asynchronousFuture.whenAnyRequest.thenRespondF {
      Future {
        blocking(countDownLatch.await())
        ResponseStub.ok(Right(""))
      }
    }
    val backend =
      PrometheusBackend(backendStub, PrometheusConfig(requestToInProgressGaugeNameMapper = _ => None))

    // when
    (0 until requestsNumber).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricSnapshot(PrometheusBackend.DefaultRequestsActiveGaugeName) shouldBe empty

    countDownLatch.countDown()
    eventually {
      getMetricValue[HistogramDataPointSnapshot](
        s"${PrometheusBackend.DefaultHistogramName}",
        List("method" -> "GET")
      ).map(_.getCount).value shouldBe requestsNumber
      getMetricSnapshot(PrometheusBackend.DefaultRequestsActiveGaugeName) shouldBe empty
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
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultSuccessCounterName,
      List("method" -> "GET", "status" -> "2xx")
    ).map(_.getValue).value shouldBe 10
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultErrorCounterName,
      List("method" -> "GET", "status" -> "4xx")
    ).map(_.getValue).value shouldBe 5
  }

  it should "not override user-supplied 'method' and 'status' labels" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend(
      backendStub,
      PrometheusConfig(
        responseToSuccessCounterMapper = (_, _) =>
          Some(
            CollectorConfig(
              collectorName = PrometheusBackend.DefaultSuccessCounterName,
              labels = List(("method", "foo"), ("status", "bar"))
            )
          )
      )
    )

    // when
    (0 until 10).foreach(_ => basicRequest.get(uri"http://127.0.0.1/foo").send(backend))

    // then
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultSuccessCounterName,
      List("method" -> "foo", "status" -> "bar")
    ).map(_.getValue).value shouldBe 10
  }

  it should "use default summary name" in {
    // given
    val response = ResponseStub("Ok", StatusCode.Ok, "Ok", Seq(Header.contentLength(10)))
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
    getMetricValue[SummaryDataPointSnapshot](PrometheusBackend.DefaultRequestSizeName, List("method" -> "GET"))
      .map(_.getCount)
      .value shouldBe 5
    getMetricValue[SummaryDataPointSnapshot](PrometheusBackend.DefaultRequestSizeName, List("method" -> "GET"))
      .map(_.getSum)
      .value shouldBe 25
    getMetricValue[SummaryDataPointSnapshot](
      PrometheusBackend.DefaultResponseSizeName,
      List("method" -> "GET", "status" -> "2xx")
    ).map(_.getCount).value shouldBe 5
    getMetricValue[SummaryDataPointSnapshot](
      PrometheusBackend.DefaultResponseSizeName,
      List("method" -> "GET", "status" -> "2xx")
    ).map(_.getSum).value shouldBe 50
  }

  it should "use error counter when http error is thrown" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondServerError()
    val backend = PrometheusBackend(backendStub)

    // when
    assertThrows[SttpClientException] {
      basicRequest
        .get(uri"http://127.0.0.1/foo")
        .response(asString.orFail)
        .send(backend)
    }

    // then
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultSuccessCounterName,
      List("method" -> "GET", "status" -> "2xx")
    ).map(_.getValue) shouldBe None
    getMetricValue[CounterDataPointSnapshot](PrometheusBackend.DefaultFailureCounterName, List("method" -> "GET"))
      .map(_.getValue) shouldBe None
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultErrorCounterName,
      List("method" -> "GET", "status" -> "5xx")
    ).map(_.getValue) shouldBe Some(1)
  }

  it should "use failure counter when other exception is thrown" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend(backendStub)

    // when
    assertThrows[IllegalStateException] {
      basicRequest
        .get(uri"http://127.0.0.1/foo")
        .response(
          asString.map(_ => throw new IllegalStateException())
        )
        .send(backend)
    }

    // then
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultSuccessCounterName,
      List("method" -> "GET", "status" -> "2xx")
    ).map(_.getValue) shouldBe None
    getMetricValue[CounterDataPointSnapshot](PrometheusBackend.DefaultFailureCounterName, List("method" -> "GET"))
      .map(_.getValue) shouldBe Some(1)
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultErrorCounterName,
      List("method" -> "GET", "status" -> "5xx")
    ).map(_.getValue) shouldBe None
  }

  it should "use success counter on success response" in {
    // given
    val backendStub = SyncBackendStub.whenAnyRequest.thenRespondOk()
    val backend = PrometheusBackend(backendStub)

    // when
    basicRequest
      .get(uri"http://127.0.0.1/foo")
      .response(asString.orFail)
      .send(backend)

    // then
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultSuccessCounterName,
      List("method" -> "GET", "status" -> "2xx")
    ).map(_.getValue) shouldBe Some(1)
    getMetricValue[CounterDataPointSnapshot](PrometheusBackend.DefaultFailureCounterName, List("method" -> "GET"))
      .map(_.getValue) shouldBe None
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultErrorCounterName,
      List("method" -> "GET", "status" -> "5xx")
    ).map(_.getValue) shouldBe None
  }

  it should "report correct host when it is extracted from the response" in {
    // given
    val backendStub =
      SyncBackendStub.whenAnyRequest.thenRespondF(_ =>
        throw new HttpError("boom", ResponseStub("", StatusCode.BadRequest))
      )

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
    getMetricValue[CounterDataPointSnapshot](
      PrometheusBackend.DefaultFailureCounterName,
      List("method" -> "GET", HostLabel -> "127.0.0.1")
    ).map(_.getValue) shouldBe Some(1)
  }

  private[this] def getMetricSnapshot[T](name: String): Option[T] =
    Option(PrometheusRegistry.defaultRegistry.scrape((s: String) => s.equals(name)))
      .filter(_.size() > 0)
      .map(_.get(0).getDataPoints.get(0))
      .map(_.asInstanceOf[T])

  private[this] def getMetricValue[T <: DataPointSnapshot](name: String, labels: List[(String, String)]): Option[T] = {
    val condition = Labels.of(labels.map(_._1).toArray, labels.map(_._2).toArray)
    Option(PrometheusRegistry.defaultRegistry.scrape((s: String) => s.equals(name)))
      .filter(_.size() > 0)
      .map(_.get(0).getDataPoints.asInstanceOf[java.util.List[T]])
      .map(_.stream().filter(_.getLabels.hasSameValues(condition)).collect(Collectors.toList[T]))
      .map(_.get(0))
  }
}
