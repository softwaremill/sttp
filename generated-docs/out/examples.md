# Usage examples

All of the examples are available [in the sources](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client/examples) in runnable form.

## POST a form using the synchronous backend

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/PostFormSynchronous.scala
    :language: scala
```

## GET and parse JSON using the akka-http backend and json4s

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "akka-http-backend" % "3.0.0-RC1",
  "com.softwaremill.sttp.client" %% "json4s" % "3.0.0-RC1",
  "org.json4s" %% "json4s-native" % "3.6.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/GetAndParseJsonAkkaHttpJson4s.scala
    :language: scala
```

## GET and parse JSON using the ZIO async-http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "3.0.0-RC1",
  "com.softwaremill.sttp.client" %% "circe" % "3.0.0-RC1",
  "io.circe" %% "circe-generic" % "0.13.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/GetAndParseJsonZioCirce.scala
    :language: scala
```

## GET and parse JSON using the async-http-client Monix backend and circe, treating deserialization errors as failed effects

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "3.0.0-RC1",
  "com.softwaremill.sttp.client" %% "circe" % "3.0.0-RC1",
  "io.circe" %% "circe-generic" % "0.13.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/GetAndParseJsonFailLeftMonixCirce.scala
    :language: scala
```

## Log requests & responses using slf4j

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "slf4j-backend" % "3.0.0-RC1",
  "com.softwaremill.sttp.client" %% "circe" % "3.0.0-RC1",
  "io.circe" %% "circe-generic" % "0.13.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/LogRequestsSlf4j.scala
    :language: scala
```

## POST and serialize JSON using the Monix async-http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "3.0.0-RC1",
  "com.softwaremill.sttp.client" %% "circe" % "3.0.0-RC1",
  "io.circe" %% "circe-generic" % "0.13.0"
)
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/PostSerializeJsonMonixAsyncHttpClientCirce.scala
    :language: scala
```

## Test an endpoint which requires multiple query parameters

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/TestEndpointMultipleQueryParameters.scala
    :language: scala
```

## Open a websocket using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/WebSocketZio.scala
    :language: scala
```

## Open a websocket using FS2 streams

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-fs2 % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/WebSocketStreamFs2.scala
    :language: scala
```

## Test Monix websockets

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-monix % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/WebSocketTesting.scala
    :language: scala
```

## Open a websocket using Akka

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "akka-http-backend" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/WebSocketAkka.scala
    :language: scala
```

## Open a websocket using Monix

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/WebSocketMonix.scala
    :language: scala
```

## Stream request and response bodies using fs2

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/StreamFs2.scala
    :language: scala
```

## Stream request and response bodies using zio-stream

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/StreamZio.scala
    :language: scala
```

## Retry a request using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/RetryZio.scala
    :language: scala
```

## GET parsed and raw response bodies

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "3.0.0-RC1")
```

Example code:

```eval_rst
.. literalinclude:: ../../examples/src/main/scala/sttp/client/examples/GetRawResponseBodySynchronous.scala
    :language: scala
```