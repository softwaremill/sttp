package sttp.client4.opentelemetry.otel4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.sdk.metrics.data.MetricData
import org.typelevel.otel4s.sdk.testkit.metrics.{MetricExpectation, MetricExpectations}
import org.typelevel.otel4s.sdk.testkit.metrics.MetricsTestkit
import org.typelevel.otel4s.semconv.metrics.HttpMetrics
import org.typelevel.otel4s.semconv.experimental.metrics.HttpExperimentalMetrics
import org.typelevel.otel4s.semconv.{MetricSpec, Requirement}
import sttp.model.{Header, StatusCode}
import sttp.client4._
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.testing.{BackendStub, ResponseStub, StubBody}

import scala.concurrent.duration._

class Otel4sMetricsBackendTest extends AsyncFreeSpec with Matchers with AsyncExecutionContext {

  "Otel4sMetricsBackend" - {
    "should pass the client semantic test" in {
      val specs = List(
        HttpMetrics.ClientRequestDuration,
        HttpExperimentalMetrics.ClientRequestBodySize,
        HttpExperimentalMetrics.ClientResponseBodySize,
        HttpExperimentalMetrics.ClientActiveRequests
      )

      MetricsTestkit
        .inMemory[IO]()
        .use { testkit =>
          implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider

          val makeBackend = Otel4sMetricsBackend(
            delegate = successStub,
            config = Otel4sMetricsConfig.default
          )

          makeBackend.use { backend =>
            for {
              _ <- backend.send(basicRequest.post(uri"http://localhost:8080/success").body("payload"))
              // we use `.unsafeRunAndForget()` in the backend and JS could be slow
              _ <- IO.sleep(1.second)
              metrics <- testkit.collectMetrics
            } yield assertMetricsMatch(metrics, specs.map(metricExpectation))
          }
        }
        .unsafeToFuture()
    }

    "should add the attributes set on the request to all recorded metrics" in {
      val metricNames = List(
        "http.client.request.duration",
        "http.client.request.body.size",
        "http.client.response.body.size",
        "http.client.active_requests"
      )

      MetricsTestkit
        .inMemory[IO]()
        .use { testkit =>
          implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider

          val makeBackend = Otel4sMetricsBackend(delegate = successStub, config = Otel4sMetricsConfig.default)

          makeBackend.use { backend =>
            for {
              _ <- backend.send(
                basicRequest
                  .post(uri"http://localhost:8080/success")
                  .body("payload")
                  .attribute(Otel4sMetricsBackend.AttributesKey, Attributes(Attribute("flow", "checkout")))
              )
              // we use `.unsafeRunAndForget()` in the backend and JS could be slow
              _ <- IO.sleep(1.second)
              metrics <- testkit.collectMetrics
            } yield {
              val flowPerMetric = metrics
                .filter(metric => metricNames.contains(metric.name))
                .map(metric =>
                  metric.name -> metric.data.points.iterator.map(_.attributes.get[String]("flow").map(_.value)).toSet
                )
                .toMap

              flowPerMetric shouldBe metricNames.map(name => name -> Set(Option("checkout"))).toMap
            }
          }
        }
        .unsafeToFuture()
    }

    "should not let the attributes set on the request override semantic convention ones" in {
      MetricsTestkit
        .inMemory[IO]()
        .use { testkit =>
          implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider

          val makeBackend = Otel4sMetricsBackend(delegate = successStub, config = Otel4sMetricsConfig.default)

          makeBackend.use { backend =>
            for {
              _ <- backend.send(
                basicRequest
                  .post(uri"http://localhost:8080/success")
                  .body("payload")
                  .attribute(
                    Otel4sMetricsBackend.AttributesKey,
                    Attributes(
                      Attribute("http.request.method", "OVERRIDDEN"),
                      Attribute("http.response.status_code", "OVERRIDDEN")
                    )
                  )
              )
              // we use `.unsafeRunAndForget()` in the backend and JS could be slow
              _ <- IO.sleep(1.second)
              metrics <- testkit.collectMetrics

              points = metrics.filter(_.name == "http.client.request.duration").flatMap(_.data.points.iterator)
              activeRequestsPoints =
                metrics.filter(_.name == "http.client.active_requests").flatMap(_.data.points.iterator)
            } yield {
              points.map(_.attributes.get[String]("http.request.method").map(_.value)).toSet shouldBe Set(Some("POST"))
              points.map(_.attributes.get[Long]("http.response.status_code").map(_.value)).toSet shouldBe Set(
                Some(200L)
              )
              activeRequestsPoints
                .map(_.attributes.get[String]("http.request.method").map(_.value))
                .toSet shouldBe Set(Some("POST"))
            }
          }
        }
        .unsafeToFuture()
    }
  }

  private def successStub = BackendStub(new CatsMonadAsyncError[IO]).whenRequestMatchesPartial {
    case r if r.uri.toString.contains("success") =>
      val body = "body"
      ResponseStub(StubBody.Adjust(body), StatusCode.Ok, Seq(Header.contentLength(body.length.toLong)))
  }

  private def metricExpectation(spec: MetricSpec): MetricExpectation = {
    val required = spec.attributeSpecs
      .filter(_.requirement.level == Requirement.Level.Required)
      .map(_.key)
      .toSet

    MetricExpectation
      .name(spec.name)
      .description(spec.description)
      .unit(spec.unit)
      .clue(spec.name)
      .where("required semantic-convention attributes are present") { metric =>
        metric.data.points.iterator
          .flatMap(_.attributes.iterator.map(_.key))
          .filter(required.contains)
          .toSet == required
      }
  }

  private def assertMetricsMatch(metrics: List[MetricData], expectations: List[MetricExpectation]) =
    MetricExpectations.checkAllDistinct(metrics, expectations) match {
      case Right(_) =>
        succeed
      case Left(mismatches) =>
        fail(MetricExpectations.format(mismatches))
    }
}
