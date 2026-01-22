/*
 * Copyright 2025 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sttp.client4.opentelemetry.otel4s

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.effect.unsafe.implicits.global
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.sdk.data.LimitedData
import org.typelevel.otel4s.sdk.testkit.trace.TracesTestkit
import org.typelevel.otel4s.sdk.trace.context.propagation.W3CTraceContextPropagator
import org.typelevel.otel4s.sdk.trace.data.{EventData, StatusData}
import org.typelevel.otel4s.trace.{StatusCode, TracerProvider}
import org.typelevel.otel4s.{Attribute, Attributes}
import sttp.client4._
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.testing.{BackendStub, ResponseStub, StubBody}
import sttp.model.{StatusCode => HttpStatusCode}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.control.NoStackTrace

class Otel4sTracingBackendTest extends AsyncFreeSpec with Matchers {

  override def executionContext: ExecutionContext = ExecutionContext.global

  "Otel4sTracingBackend" - {
    "should add tracing headers to the request" in {
      TracesTestkit
        .builder[IO]
        .addTracerProviderCustomizer(_.addTextMapPropagators(W3CTraceContextPropagator.default))
        .build
        .use { testkit =>
          implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider

          def stub = BackendStub(new CatsMonadAsyncError[IO])
            .whenRequestMatchesPartial { r =>
              assert(r.header("traceparent").isDefined)
              ResponseStub.ok(StubBody.Adjust(""))
            }

          val makeBackend = Otel4sTracingBackend(stub, Otel4sTracingConfig.default)

          for {
            backend <- makeBackend
            response <- backend.send(basicRequest.get(uri"success"))
          } yield response.code shouldBe HttpStatusCode.Ok
        }
        .unsafeToFuture()
    }

    "should record request/response-specific attributes: 200 OK response" in {
      TracesTestkit
        .builder[IO]
        .addTracerProviderCustomizer(_.addTextMapPropagators(W3CTraceContextPropagator.default))
        .build
        .use { testkit =>
          implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider

          def stub = BackendStub(new CatsMonadAsyncError[IO]).whenRequestMatchesPartial {
            case r if r.uri.toString.contains("success") =>
              assert(r.header("traceparent").isDefined)
              ResponseStub.ok(StubBody.Adjust(""))
          }

          val makeBackend = Otel4sTracingBackend(stub, Otel4sTracingConfig.default)

          for {
            backend <- makeBackend
            response <- backend.send(basicRequest.get(uri"http://user:pwd@localhost:8080/success?q=v"))
            spans <- testkit.finishedSpans
          } yield {
            val status = StatusData(StatusCode.Unset)

            val attributes = Attributes(
              Attribute("http.request.method", "GET"),
              Attribute("http.response.status_code", 200L),
              Attribute("server.address", "localhost"),
              Attribute("server.port", 8080L),
              Attribute("url.full", "http://REDACTED:REDACTED@localhost:8080/success?q=v"),
              Attribute("url.scheme", "http")
            )

            response.code shouldBe HttpStatusCode.Ok

            spans.map(_.attributes.elements) shouldBe List(attributes)
            spans.map(_.events.elements) shouldBe List(Vector.empty)
            spans.map(_.status) shouldBe List(status)

            succeed
          }
        }
        .unsafeToFuture()
    }

    "should record request/response-specific attributes: 400 BadRequest response" in {
      TracesTestkit
        .builder[IO]
        .addTracerProviderCustomizer(_.addTextMapPropagators(W3CTraceContextPropagator.default))
        .build
        .use { testkit =>
          implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider

          def stub = BackendStub(new CatsMonadAsyncError[IO]).whenRequestMatchesPartial {
            case r if r.uri.toString.contains("bad-request") =>
              assert(r.header("traceparent").isDefined)
              ResponseStub(StubBody.Adjust(""), HttpStatusCode.BadRequest)
          }

          val makeBackend = Otel4sTracingBackend(stub, Otel4sTracingConfig.default)

          for {
            backend <- makeBackend
            response <- backend.send(basicRequest.get(uri"http://user@localhost:8080/bad-request?q=v"))
            spans <- testkit.finishedSpans
          } yield {
            val status = StatusData(StatusCode.Error)

            val attributes = Attributes(
              Attribute("http.request.method", "GET"),
              Attribute("http.response.status_code", 400L),
              Attribute("server.address", "localhost"),
              Attribute("server.port", 8080L),
              Attribute("url.full", "http://REDACTED@localhost:8080/bad-request?q=v"),
              Attribute("url.scheme", "http"),
              Attribute("error.type", "400")
            )

            response.code shouldBe HttpStatusCode.BadRequest

            spans.map(_.attributes.elements) shouldBe List(attributes)
            spans.map(_.events.elements) shouldBe List(Vector.empty)
            spans.map(_.status) shouldBe List(status)

            succeed
          }
        }
        .unsafeToFuture()
    }

    "should record request/response-specific attributes: runtime error" in {
      TestControl
        .executeEmbed {
          TracesTestkit
            .builder[IO]
            .addTracerProviderCustomizer(_.addTextMapPropagators(W3CTraceContextPropagator.default))
            .build
            .use { testkit =>
              implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider

              object Err extends RuntimeException("Something went wrong") with NoStackTrace

              def stub = BackendStub(new CatsMonadAsyncError[IO]).whenRequestMatchesPartial {
                case r if r.uri.toString.contains("error") =>
                  assert(r.header("traceparent").isDefined)
                  throw Err
              }

              val makeBackend = Otel4sTracingBackend(stub, Otel4sTracingConfig.default)

              for {
                backend <- makeBackend
                response <- backend.send(basicRequest.get(uri"http://localhost/error")).attempt
                spans <- testkit.finishedSpans
              } yield {
                val status = StatusData(StatusCode.Error)

                val attributes = Attributes(
                  Attribute("http.request.method", "GET"),
                  Attribute("server.address", "localhost"),
                  Attribute("url.full", "http://localhost/error"),
                  Attribute("url.scheme", "http"),
                  Attribute("error.type", Err.getClass.getName)
                )

                val event = EventData.fromException(
                  Duration.Zero,
                  Err,
                  LimitedData.attributes(128, 128)
                )

                response shouldBe Left(Err)

                spans.map(_.attributes.elements) shouldBe List(attributes)
                spans.map(_.events.elements) shouldBe List(Vector(event))
                spans.map(_.status) shouldBe List(status)

                succeed
              }
            }
        }
        .unsafeToFuture()
    }
  }

}
