.. _akkahttp:

akka-http backend
=================

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "akka-http-backend" % "2.0.0-M6"

This backend depends on `akka-http <http://doc.akka.io/docs/akka-http/current/scala/http/>`_. A fully **asynchronous** backend. Sending a request returns a response wrapped in a ``Future``.

Note that you'll also need an explicit dependency on akka-streams, as akka-http doesn't depend on any specific akka-streams version. So you'll also need to add, for example::

  "com.typesafe.akka" %% "akka-stream" % "2.5.11"

Next you'll need to add an implicit value::

  implicit val sttpBackend = AkkaHttpBackend()
  
  // or, if you'd like to use an existing actor system:
  implicit val sttpBackend = AkkaHttpBackend.usingActorSystem(actorSystem)

This backend supports sending and receiving `akka-streams <http://doc.akka.io/docs/akka/current/scala/stream/index.html>`_ streams of type ``akka.stream.scaladsl.Source[ByteString, Any]``.

To set the request body as a stream::

  import sttp.client._
  import sttp.client.akkahttp._
  
  import akka.stream.scaladsl.Source
  import akka.util.ByteString
  
  val source: Source[ByteString, Any] =   ...
  
  basicRequest
    .streamBody(source)
    .post(uri"...")

To receive the response body as a stream::

  import sttp.client._
  import sttp.client.akkahttp._
  
  import akka.stream.scaladsl.Source
  import akka.util.ByteString
  
  implicit val sttpBackend = AkkaHttpBackend()
  
  val response: Future[Response[Either[String, Source[ByteString, Any]]]] =
    basicRequest
      .post(uri"...")
      .response(asStream[Source[ByteString, Any]])
      .send()
    

Testing
-------

For testing, you can create a backend using any `HttpRequest => Future[HttpResponse]` function, or an akka-http `Route`.

That way, you can "mock" a server that the backend will talk to, without starting any actual server or making any HTTP calls.

If your application provides a client library for its dependants to use, this is a great way to ensure that the client
actually matches the routes exposed by your application::

  val backend: SttpBackend[Future, Nothing, Flow[Message, Message, *]] = {
    AkkaHttpBackend.usingClient(system, http = AkkaHttpClient.stubFromRoute(Routes.route))
  }

Websockets
----------

The akka-http backend supports websockets, where the websocket handler is of type ``akka.stream.scaladsl.Flow[Message, Message, _]``. That is, when opening a websocket connection, you need to provide the description of a stream, which will consume incoming websocket messages, and produce outgoing websocket messages. For example::

  import akka.Done
  import akka.stream.scaladsl.Flow
  import akka.http.scaladsl.model.ws.Message

  import sttp.client._
  import sttp.client.ws.WebSocketResponse

  import scala.concurrent.Future

  val flow: Flow[Message, Message, Future[Done]] = ...
  val response: Future[WebSocketResponse[Future[Done]]] =
      basicRequest.get(uri"wss://echo.websocket.org").openWebsocket(flow)

In this example, the given flow materialises to a ``Future[Done]``, however this value can be arbitrary and depends on the shape and definition of the message-processing stream. The ``Future[WebSocketResponse]`` will complete once the websocket is established and contain the materialised value.
