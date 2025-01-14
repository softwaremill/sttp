## Hello, World!

* [Dynamic URI components](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/dynamicUriSynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [POST form data](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/PostFormSynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [POST multipart form](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/postMultipartFormSynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [Post JSON data](https://github.com/softwaremill/sttp/tree/master/examples-ce2/src/main/scala/sttp/client4/examples/PostSerializeJsonMonixHttpClientCirce.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Monix</span>
* [Upload file](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/fileUploadSynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>

## Backend wrapper

* [A backend which adds a header to all outgoing requests](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/wrapper/addHeaderBackend.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Synchronous</span>
* [Integrate with resilience4j to implement circuit-breaking](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/wrapper/CircuitBreakerCatsEffect.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>
* [Integrate with resilience4j to implement rate-limiting](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/wrapper/rateLimiterFuture.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Future</span>
* [Report metrics to a cloud service](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/wrapper/metricsWrapperPekkoHttp.scala) <span class="example-tag example-backend">Pekko</span> <span class="example-tag example-effects">Future</span>
* [Simple retrying backend wrapper](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/wrapper/retryingBackend.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Synchronous</span>
* [Use the caching backend wrapper with Redis](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/wrapper/redisCachingBackend.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Synchronous</span>

## JSON

* [Receive & parse JSON using ZIO Json](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/json/GetAndParseJsonZioJson.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>
* [Receive & parse JSON using circe](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/json/GetAndParseJsonCatsEffectCirce.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>
* [Receive & parse JSON using circe](https://github.com/softwaremill/sttp/tree/master/examples-ce2/src/main/scala/sttp/client4/examples/GetAndParseJsonOrFailMonixCirce.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Monix</span>
* [Receive & parse JSON using json4s](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/json/getAndParseJsonPekkoHttpJson4s.scala) <span class="example-tag example-backend">Pekko</span> <span class="example-tag example-effects">Future</span>
* [Receive & parse JSON using jsoniter](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/json/getAndParseJsonSynchronousJsoniter.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>

## Logging

* [Add a logging backend wrapper, which uses slf4j](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/LogRequestsSlf4j.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>

## Other

* [Handle the body by both parsing it to JSON and returning the raw string](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/GetRawResponseBodySynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>

## Resilience

* [Rate limit sending requests using Ox](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/resilience/RateLimitOx.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [Retry sending a request using Ox](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/resilience/RetryOx.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [Retry sending a request using ZIO's retries](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/resilience/RetryZio.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>

## Streaming

* [Stream request & response bodies using ZIO-Streams](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/StreamZio.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>
* [Stream request & response bodies using fs2](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/StreamFs2.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>

## Testing

* [Create a backend stub which simulates interactions using multiple query parameters](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/testing/TestEndpointMultipleQueryParameters.scala) 
* [Create a backend stub which simulates interactions with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/testing/WebSocketTesting.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>

## WebSocket

* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/ws/WebSocketPekko.scala) <span class="example-tag example-backend">Pekko</span> <span class="example-tag example-effects">Future</span>
* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/ws/WebSocketZio.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>
* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/ws/WebSocketSynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples-ce2/src/main/scala/sttp/client4/examples/WebSocketMonix.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Monix</span>
* [Connect to & interact with a WebSocket, using Ox channels for streaming](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/ws/wsOxExample.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [Connect to & interact with a WebSocket, using fs2 streaming](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/ws/WebSocketStreamFs2.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>