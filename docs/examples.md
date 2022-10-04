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