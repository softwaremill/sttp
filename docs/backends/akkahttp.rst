.. _akkahttp:

akka-http backend
=================

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %% "akka-http-backend" % "1.7.0"

This backend depends on `akka-http <http://doc.akka.io/docs/akka-http/current/scala/http/>`_. A fully **asynchronous** backend. Sending a request returns a response wrapped in a ``Future``.

Note that you'll also need an explicit dependency on akka-streams, as akka-http doesn't depend on any specific akka-streams version. So you'll also need to add, for example::

  "com.typesafe.akka" %% "akka-stream" % "2.5.11"

Next you'll need to add an implicit value::

  implicit val sttpBackend = AkkaHttpBackend()
  
  // or, if you'd like to use an existing actor system:
  implicit val sttpBackend = AkkaHttpBackend.usingActorSystem(actorSystem)

This backend supports sending and receiving `akka-streams <http://doc.akka.io/docs/akka/current/scala/stream/index.html>`_ streams of type ``akka.stream.scaladsl.Source[ByteString, Any]``.

To set the request body as a stream::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.akkahttp._
  
  import akka.stream.scaladsl.Source
  import akka.util.ByteString
  
  val source: Source[ByteString, Any] =   ...
  
  sttp
    .streamBody(source)
    .post(uri"...")

To receive the response body as a stream::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.akkahttp._
  
  import akka.stream.scaladsl.Source
  import akka.util.ByteString
  
  implicit val sttpBackend = AkkaHttpBackend()
  
  val response: Future[Response[Source[ByteString, Any]]] = 
    sttp
      .post(uri"...")
      .response(asStream[Source[ByteString, Any]])
      .send()
    

Testing
-------

For testing, you can create a backend using any `HttpRequest => Future[HttpResponse]` function, or an akka-http `Route`.

That way, you can "mock" a server that the backend will talk to, without starting any actual server or making any HTTP calls.

If your application provides a client library for its dependants to use, this is a great way to ensure that the client
actually matches the routes exposed by your application::

  val backend: SttpBackend[Future, Nothing] = {
    AkkaHttpBackend.usingClient(system, http = AkkaHttpClient.stubFromRoute(Routes.route))
  }
