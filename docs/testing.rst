Testing
=======

If you need a stub backend for use in tests instead of a "real" backend (you probably don't want to make HTTP calls during unit tests), you can use the ``SttpBackendStub`` class. It allows specifying how the backend should respond to requests matching given predicates.

You can also create a stub backend using :ref:`akka-http routes <akkahttp>`.

Creating a stub backend
-----------------------

An empty backend stub can be created using the following ways:

* by using one of the factory methods ``SttpBackendStub.synchronous`` or ``SttpBackendStub.asynchronousFuture``, which return stubs which use the ``Id`` or standard Scala's ``Future`` response wrappers without streaming support
* by explicitly giving the response wrapper monad and supported streams type, e.g. ``SttpBackendStub[Task, Observable[ByteBuffer]](TaskMonad)``
* given an instance of a "real" backend, e.g. ``SttpBackendStub(HttpURLConnectionBackend())`` or ``SttpBackendStub(AsyncHttpClientScalazBackend())``. The stub will then use the same response wrapper and support the same type of streams as the given "real" backend.
* by specifying a fallback/delegate backend, see below

Specifying behavior
-------------------

Behavior of the stub can be specified using a combination of the ``whenRequestMatches`` and ``thenRespond`` methods::

  implicit val testingBackend = SttpBackendStub.synchronous
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespond("Hello there!")
    .whenRequestMatches(_.method == Method.POST)
    .thenRespondServerError()

  val response1 = sttp.get(uri"http://example.org/a/b/c").send()
  // response1.body will be Right("Hello there")

  val response2 = sttp.post(uri"http://example.org/d/e").send()
  // response2.code will be 500

It is also possible to match requests by partial function, returning a response. E.g.::

  implicit val testingBackend = SttpBackendStub.synchronous
    .whenRequestMatchesPartial({
      case r if r.uri.path.endsWith(List("partial10")) =>
        Response.error("Not found", 404)

      case r if r.uri.path.endsWith(List("partialAda")) =>
        // additional verification of the request is possible
        assert(r.body == StringBody("z"))
        Response.ok("Ada")
    })

  val response1 = sttp.get(uri"http://example.org/partial10").send()
  // response1.body will be Right(10)

  val response2 = sttp.post(uri"http://example.org/partialAda").send()
  // response2.body will be Right("Ada")

This approach to testing has one caveat: the responses are not type-safe. That is, the stub backend cannot match on or verify that the type of the response body matches the response body type requested.

Another way to specify the behaviour is passing response wrapped in the result monad to the stub. It is useful if you need to test a scenario with a slow server, when the response should be not returned immediately, but after some time. Example with Futures: ::

  implicit val testingBackend = SttpBackendStub.asynchronousFuture.whenAnyRequest
    .thenRespondWrapped(Future {
      Thread.sleep(5000)
      Response(Right("OK"), 200, "", Nil, Nil)
    })

  val responseFuture = sttp.get(uri"http://example.org").send()
  // responseFuture will complete after 5 seconds with "OK" response

The returned response may also depend on the request: ::

  implicit val testingBackend = SttpBackendStub.synchronous.whenAnyRequest
    .thenRespondWrapped(req =>
      Response(Right("OK, got request sent to ${req.uri.host}"), 200, "", Nil, Nil)
    )

  val response = sttp.get(uri"http://example.org").send()
  // response.body will be Right("OK, got request sent to example.org")


You can define consecutive raw responses that will be served: ::

  implicit val testingBackend = SttpBackendStub.synchronous.whenAnyRequest
    .thenRespondCyclic("first", "second", "third")

  sttp.get(uri"http://example.org").send()       // Right("OK, first")
  sttp.get(uri"http://example.org").send()       // Right("OK, second")
  sttp.get(uri"http://example.org").send()       // Right("OK, third")
  sttp.get(uri"http://example.org").send()       // Right("OK, first")

Or multiple `Response` instances: ::

  implicit val testingBackend = SttpBackendStub.synchronous.whenAnyRequest
    .thenRespondCyclicResponses(
      Response.ok[String]("first"),
      Response.error[String]("error", 500, "Something went wrong")
    )

  sttp.get(uri"http://example.org").send()       // code will be 200
  sttp.get(uri"http://example.org").send()       // code will be 500
  sttp.get(uri"http://example.org").send()       // code will be 200


Simulating exceptions
---------------------

If you want to simulate an exception being thrown by a backend, e.g. a socket timeout exception, you can do so by throwing the appropriate exception instead of the response, e.g.::

  implicit val testingBackend = SttpBackendStub.synchronous
    .whenRequestMatches(_ => true)
    .thenRespond(throw new TimeoutException())

Adjusting the response body type
--------------------------------

If the type of the response body returned by the stub's rules (as specified using the ``.whenXxx`` methods) doesn't match what was specified in the request, the stub will attempt to convert the body to the desired type. This might be useful when:

* testing code which maps a basic response body to a custom type, e.g. mapping a raw json string using a decoder to a domain type
* reading a classpath resource (which results in an ``InputStream``) and requesting a response of e.g. type ``String``

The following conversions are supported:

* anything to ``()`` (unit), when the response is ignored
* ``InputStream`` and ``Array[Byte]`` to ``String``
* ``InputStream`` and ``String`` to ``Array[Byte]``
* ``InputStream``, ``String`` and ``Array[Byte]`` to custom types through mapped response specifications

Example: returning JSON
-----------------------

For example, if you want to return a JSON response, simply use `.withResponse(String)` as below:::

  implicit val testingBackend = SttpBackendStub.synchronous
    .whenRequestMatches(_ => true)
    .thenRespond("" {"username": "john", "age": 65 } """)

  def parseUserJson(a: Array[Byte]): User = ...

  val response = sttp.get(uri"http://example.com")
    .response(asByteArray.map(parseUserJson))
    .send()

In the example above, the stub's rules specify that a response with a ``String``-body should be returned for any request; the request, on the other hand, specifies that response body should be parsed from a byte array to a custom ``User`` type. These type don't match, so the ``SttpBackendStub`` will in this case convert the body to the desired type.

Note that no conversions will be attempted for streaming response bodies.

Delegating to another backend
-----------------------------

It is also possible to create a stub backend which delegates calls to another (possibly "real") backend if none of the specified predicates match a request. This can be useful during development, to partially stub a yet incomplete API with which we integrate::

  implicit val testingBackend =
    SttpBackendStub.withFallback(HttpURLConnectionBackend())
      .whenRequestMatches(_.uri.path.startsWith(List("a")))
      .thenRespond("I'm a STUB!")

  val response1 = sttp.get(uri"http://api.internal/a").send()
  // response1.body will be Right("I'm a STUB")

  val response2 = sttp.post(uri"http://api.internal/b").send()
  // response2 will be whatever a "real" network call to api.internal/b returns

