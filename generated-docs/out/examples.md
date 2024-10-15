# Usage examples

All of the examples are available [in the sources](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client4/examples) in runnable form.

## Use the simple synchronous client

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "core" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/SimpleClientGetAndPost.scala
    :language: scala
```

## POST a form using the synchronous backend

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "core" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/PostFormSynchronous.scala
    :language: scala
```

## GET and parse JSON using the akka-http backend and json4s

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client4" %% "akka-http-backend" % "4.0.0-M19",
  "com.softwaremill.sttp.client4" %% "json4s" % "4.0.0-M19",
  "org.json4s" %% "json4s-native" % "3.6.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/GetAndParseJsonAkkaHttpJson4s.scala
    :language: scala
```

## GET and parse JSON using the ZIO http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client4" %% "zio" % "4.0.0-M19",
  "com.softwaremill.sttp.client4" %% "circe" % "4.0.0-M19",
  "io.circe" %% "circe-generic" % "0.14.10"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/GetAndParseJsonZioCirce.scala
    :language: scala
```

## GET and parse JSON using the http-client Monix backend and circe, treating deserialization errors as failed effects

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client4" %% "monix" % "4.0.0-M19",
  "com.softwaremill.sttp.client4" %% "circe" % "4.0.0-M19",
  "io.circe" %% "circe-generic" % "0.14.10"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client4/examples/GetAndParseJsonGetRightMonixCirce.scala
    :language: scala
```

## Log requests & responses using slf4j

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client4" %% "slf4j-backend" % "4.0.0-M19",
  "com.softwaremill.sttp.client4" %% "circe" % "4.0.0-M19",
  "io.circe" %% "circe-generic" % "0.14.10"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/LogRequestsSlf4j.scala
    :language: scala
```

## POST and serialize JSON using the Monix http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client4" %% "monix" % "4.0.0-M19",
  "com.softwaremill.sttp.client4" %% "circe" % "4.0.0-M19",
  "io.circe" %% "circe-generic" % "0.14.10"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client4/examples/PostSerializeJsonMonixHttpClientCirce.scala
    :language: scala
```

## Test an endpoint which requires multiple query parameters

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "core" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/TestEndpointMultipleQueryParameters.scala
    :language: scala
```
## Open a websocket using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "zio" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/WebSocketZio.scala
    :language: scala
```

## Open a websocket using FS2 streams

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "fs2" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/WebSocketStreamFs2.scala
    :language: scala
```

## Test Monix websockets

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "monix" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client4/examples/WebSocketTesting.scala
    :language: scala
```

## Open a websocket using Akka

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "akka-http-backend" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/WebSocketAkka.scala
    :language: scala
```

## Open a websocket using Pekko

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "pekko-http-backend" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/WebSocketPekko.scala
    :language: scala
```

## Open a websocket using Monix

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "monix" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client4/examples/WebSocketMonix.scala
    :language: scala
```

## Stream request and response bodies using fs2

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "fs2" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/StreamFs2.scala
    :language: scala
```

## Stream request and response bodies using zio-stream

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "zio" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/StreamZio.scala
    :language: scala
```

## Retry a request using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "zio" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/RetryZio.scala
    :language: scala
```

## GET parsed and raw response bodies

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client4" %% "core" % "4.0.0-M19")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client4/examples/GetRawResponseBodySynchronous.scala
    :language: scala
```