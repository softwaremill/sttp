Testing
=======

If you need a stub backend for use in tests instead of a "real" backend (you probably don't want to make HTTP calls during unit tests), you can use the ``SttpBackendStub`` class. It allows specifying how the backend should respond to requests matching given predicates.

A backend stub can be created using an instance of a "real" backend, or by explicitly giving the response wrapper monad and supported streams type.

For example::

  implicit val testingBackend = SttpBackendStub(HttpURLConnectionBackend())
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespond("Hello there!")
    .whenRequestMatches(_.method == Method.POST)
    .thenRespondServerError()
      
  val response1 = sttp.get(uri"http://example.org/a/b/c").send()
  // response1.body will be Right("Hello there")
  
  val response2 = sttp.post(uri"http://example.org/d/e").send()
  // response2.code will be 500

It is also possible to match request by partial function, returning a response. E.g.::

  implicit val testingBackend = SttpBackendStub(HttpURLConnectionBackend())
    .whenRequestMatchesPartial({
      case r if r.uri.path.endsWith(List("partial10")) => Response(Right(10), 200, Nil, Nil)
      case r if r.uri.path.endsWith(List("partialAda")) => Response(Right("Ada"), 200, Nil, Nil)
    })

  val response1 = sttp.get(uri"http://example.org/partial10").send()
  // response1.body will be Right(10)

  val response2 = sttp.post(uri"http://example.org/partialAda").send()
  // response2.body will be Right("Ada")

However, this approach has one caveat: the responses are not type-safe. That is, the backend cannot match on or verify that the type included in the response matches the response type requested.

Simulating exceptions
---------------------

If you want to simulate an exception being thrown by a backend, e.g. a socket timeout exception, you can do so by throwing the appropriate exception instead of the response, e.g.::

  implicit val testingBackend = SttpBackendStub(HttpURLConnectionBackend())
    .whenRequestMatches(_ => true)
    .thenRespond(throw new TimeoutException())

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

