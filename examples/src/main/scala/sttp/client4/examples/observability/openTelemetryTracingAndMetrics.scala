// {cat=Observability; effects=Synchronous; backend=HttpClient}: Use the OpenTelemetry tracing & metrics wrappers

//> using dep com.softwaremill.sttp.client4::opentelemetry-backend:4.0.13
//> using dep io.opentelemetry:opentelemetry-exporter-otlp:1.58.0
//> using dep io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.58.0

package sttp.client4.examples.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

import sttp.client4.*
import sttp.client4.opentelemetry.*
import sttp.client4.httpclient.HttpClientSyncBackend

/*

To see the metrics & spans in action, you'll need an OpenTelemetry collector running. There's a couple of options,
one being the Grafana stack. You can run it using docker-compose; the UI is available at http://localhost:3000
using admin/admin:

services:
  observability:
    image: 'grafana/otel-lgtm'
    ports:
      - '3000:3000' # Grafana's UI
      - '4317:4317' # Exporter

 */

@main def openTelemetryTracingAndMetrics(): Unit =
  val otel: OpenTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk()

  val baseBackend: SyncBackend = HttpClientSyncBackend()
  val backend = OpenTelemetryTracingBackend(OpenTelemetryMetricsBackend(baseBackend, otel), otel)

  val response = basicRequest
    .post(uri"http://httpbin.org/post")
    .body("Hello, sttp v4!")
    .send(backend)

  println(response.show())

  Thread.sleep(5000) // wait for the metrics to be sent
