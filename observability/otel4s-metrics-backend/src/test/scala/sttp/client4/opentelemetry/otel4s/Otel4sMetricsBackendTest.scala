package sttp.client4.opentelemetry.otel4s

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.sdk.metrics.data.MetricData
import org.typelevel.otel4s.sdk.testkit.metrics.MetricsTestkit
import org.typelevel.otel4s.semconv.experimental.metrics.HttpExperimentalMetrics
import org.typelevel.otel4s.semconv.metrics.HttpMetrics
import org.typelevel.otel4s.semconv.{MetricSpec, Requirement}
import sttp.model.{Header, StatusCode}
import sttp.client4._
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.testing.{BackendStub, ResponseStub, StubBody}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Otel4sMetricsBackendTest extends AsyncFreeSpec with Matchers {

  override def executionContext: ExecutionContext = ExecutionContext.global

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

          def stub = BackendStub(new CatsMonadAsyncError[IO]).whenRequestMatchesPartial {
            case r if r.uri.toString.contains("success") =>
              val body = "body"
              ResponseStub(StubBody.Adjust(body), StatusCode.Ok, Seq(Header.contentLength(body.length.toLong)))
          }

          val makeBackend = Otel4sMetricsBackend(
            delegate = stub,
            config = Otel4sMetricsConfig.default
          )

          makeBackend.use { backend =>
            for {
              r <- backend.send(basicRequest.post(uri"http://localhost:8080/success").body("payload"))
              // we use `.unsafeRunAndForget()` in the backend and JS could be slow
              _ <- IO.sleep(1.second)
              metrics <- testkit.collectMetrics
              _ = specs.foreach(spec => specTest(metrics, spec))
            } yield succeed
          }
        }
        .unsafeToFuture()
    }
  }

  private def specTest(metrics: List[MetricData], spec: MetricSpec): Unit = {
    val metric = metrics.find(_.name == spec.name)
    assert(
      metric.isDefined,
      s"${spec.name} metric is missing. Available [${metrics.map(_.name).mkString(", ")}]"
    )

    metric.foreach { md =>
      md.name shouldBe spec.name
      md.description shouldBe Some(spec.description)
      md.unit shouldBe Some(spec.unit)

      val required = spec.attributeSpecs
        .filter(_.requirement.level == Requirement.Level.Required)
        .map(_.key)
        .toSet

      val current = md.data.points.toVector
        .flatMap(_.attributes.map(_.key))
        .filter(key => required.contains(key))
        .toSet

      current shouldBe required
    }
  }
}
