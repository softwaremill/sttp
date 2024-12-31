## Hello, World!

* [Post JSON data](https://github.com/softwaremill/sttp/tree/master/examples-ce2/src/main/scala/sttp/client4/examples/PostSerializeJsonMonixHttpClientCirce.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Monix</span>
* [Post form data](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/PostFormSynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>

## JSON

* [Receive & parse JSON using circe](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/GetAndParseJsonZioCirce.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>
* [Receive & parse JSON using circe](https://github.com/softwaremill/sttp/tree/master/examples-ce2/src/main/scala/sttp/client4/examples/GetAndParseJsonOrFailMonixCirce.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Monix</span>
* [Receive & parse JSON using json4s](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/getAndParseJsonPekkoHttpJson4s.scala) <span class="example-tag example-backend">Pekko</span> <span class="example-tag example-effects">Future</span>

## Logging

* [Add a logging backend wrapper, which uses slf4j](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/LogRequestsSlf4j.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>

## Other

* [Handle the body by both parsing it to JSON and returning the raw string](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/GetRawResponseBodySynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>

## Resilience

* [Retry sending a request](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/RetryZio.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>

## Streaming

* [Stream request & response bodies using ZIO-Streams](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/StreamZio.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>
* [Stream request & response bodies using fs2](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/StreamFs2.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>

## Testing

* [Create a backend stub which simulates interactions using multiple query parameters](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/TestEndpointMultipleQueryParameters.scala) 
* [Create a backend stub which simulates interactions with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/WebSocketTesting.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>

## WebSocket

* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/WebSocketPekko.scala) <span class="example-tag example-backend">Pekko</span> <span class="example-tag example-effects">Future</span>
* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/WebSocketZio.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">ZIO</span>
* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/WebSocketSynchronous.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [Connect to & interact with a WebSocket](https://github.com/softwaremill/sttp/tree/master/examples-ce2/src/main/scala/sttp/client4/examples/WebSocketMonix.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Monix</span>
* [Connect to & interact with a WebSocket, using Ox channels for streaming](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/wsOxExample.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">Direct</span>
* [Connect to & interact with a WebSocket, using fs2 streaming](https://github.com/softwaremill/sttp/tree/master/examples/src/main/scala/sttp/client4/examples/WebSocketStreamFs2.scala) <span class="example-tag example-backend">HttpClient</span> <span class="example-tag example-effects">cats-effect</span>