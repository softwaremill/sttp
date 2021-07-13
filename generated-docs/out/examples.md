# Usage examples

All of the examples are available [in the sources](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client3/examples) in runnable form.

## POST a form using the synchronous backend

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "core" % "3.3.10")
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
  "com.softwaremill.sttp.client3" %% "akka-http-backend" % "3.3.10",
  "com.softwaremill.sttp.client3" %% "json4s" % "3.3.10",
  "org.json4s" %% "json4s-native" % "3.6.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/GetAndParseJsonAkkaHttpJson4s.scala
    :language: scala
```

## GET and parse JSON using the ZIO async-http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.3.10",
  "com.softwaremill.sttp.client3" %% "circe" % "3.3.10",
  "io.circe" %% "circe-generic" % "0.14.1"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/GetAndParseJsonZioCirce.scala
    :language: scala
```

## GET and parse JSON using the async-http-client Monix backend and circe, treating deserialization errors as failed effects

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-monix" % "3.3.10",
  "com.softwaremill.sttp.client3" %% "circe" % "3.3.10",
  "io.circe" %% "circe-generic" % "0.14.1"
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
  "com.softwaremill.sttp.client3" %% "slf4j-backend" % "3.3.10",
  "com.softwaremill.sttp.client3" %% "circe" % "3.3.10",
  "io.circe" %% "circe-generic" % "0.14.1"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/LogRequestsSlf4j.scala
    :language: scala
```

## POST and serialize JSON using the Monix async-http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-monix" % "3.3.10",
  "com.softwaremill.sttp.client3" %% "circe" % "3.3.10",
  "io.circe" %% "circe-generic" % "0.14.1"
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
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "core" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/TestEndpointMultipleQueryParameters.scala
    :language: scala
```

## Open a websocket using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/WebSocketZio.scala
    :language: scala
```

## Open a websocket using FS2 streams

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2 % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/WebSocketStreamFs2.scala
    :language: scala
```

## Test Monix websockets

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "async-http-client-backend-monix" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client3/examples/WebSocketTesting.scala
    :language: scala
```

## Open a websocket using Akka

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "akka-http-backend" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/WebSocketAkka.scala
    :language: scala
```

## Open a websocket using Monix

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "async-http-client-backend-monix" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples-ce2/src/main/scala/sttp/client3/examples/WebSocketMonix.scala
    :language: scala
```

## Stream request and response bodies using fs2

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/StreamFs2.scala
    :language: scala
```

## Stream request and response bodies using zio-stream

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/StreamZio.scala
    :language: scala
```

## Retry a request using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/RetryZio.scala
    :language: scala
```

## GET parsed and raw response bodies

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client3" %% "core" % "3.3.10")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client3/examples/GetRawResponseBodySynchronous.scala
    :language: scala
```