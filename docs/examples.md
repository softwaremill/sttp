# Usage examples

All of the examples are available [in the sources](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client3/examples) in runnable form.

## Use the simple synchronous client

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "core" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/SimpleClientGetAndPost.scala
    :language: scala
```

## POST a form using the synchronous backend

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "core" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/PostFormSynchronous.scala
    :language: scala
```

## GET and parse JSON using the akka-http backend and json4s

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "akka-http-backend" % "@VERSION@",
  "com.softwaremill.sttp.client3" %% "json4s" % "@VERSION@",
  "org.json4s" %% "json4s-native" % "3.6.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/GetAndParseJsonAkkaHttpJson4s.scala
    :language: scala
```

## GET and parse JSON using the ZIO http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "zio" % "@VERSION@",
  "com.softwaremill.sttp.client3" %% "circe" % "@VERSION@",
  "io.circe" %% "circe-generic" % "@CIRCE_VERSION@"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/GetAndParseJsonZioCirce.scala
    :language: scala
```

## GET and parse JSON using the http-client Monix backend and circe, treating deserialization errors as failed effects

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "monix" % "@VERSION@",
  "com.softwaremill.sttp.client3" %% "circe" % "@VERSION@",
  "io.circe" %% "circe-generic" % "@CIRCE_VERSION@"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client3/examples/GetAndParseJsonGetRightMonixCirce.scala
    :language: scala
```

## Log requests & responses using slf4j

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "slf4j-backend" % "@VERSION@",
  "com.softwaremill.sttp.client3" %% "circe" % "@VERSION@",
  "io.circe" %% "circe-generic" % "@CIRCE_VERSION@"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/LogRequestsSlf4j.scala
    :language: scala
```

## POST and serialize JSON using the Monix http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "monix" % "@VERSION@",
  "com.softwaremill.sttp.client3" %% "circe" % "@VERSION@",
  "io.circe" %% "circe-generic" % "@CIRCE_VERSION@"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client3/examples/PostSerializeJsonMonixAsyncHttpClientCirce.scala
    :language: scala
```

## Test an endpoint which requires multiple query parameters

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "core" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/TestEndpointMultipleQueryParameters.scala
    :language: scala
```
## Open a websocket using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "zio" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/WebSocketZio.scala
    :language: scala
```

## Open a websocket using FS2 streams

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "fs2" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/WebSocketStreamFs2.scala
    :language: scala
```

## Test Monix websockets

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "monix" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client3/examples/WebSocketTesting.scala
    :language: scala
```

## Open a websocket using Akka

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "akka-http-backend" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/WebSocketAkka.scala
    :language: scala
```

## Open a websocket using Monix

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "monix" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client3/examples/WebSocketMonix.scala
    :language: scala
```

## Stream request and response bodies using fs2

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "fs2" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/StreamFs2.scala
    :language: scala
```

## Stream request and response bodies using zio-stream

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "zio" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/StreamZio.scala
    :language: scala
```

## Retry a request using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "zio" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/RetryZio.scala
    :language: scala
```

## GET parsed and raw response bodies

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "core" % "@VERSION@")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/GetRawResponseBodySynchronous.scala
    :language: scala
```