.. _httpclient:

HttpClient backend (Java 11+)
=============================

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "httpclient-backend" % "2.0.0-RC3"

This backend is based on ``java.net.http.HttpClient`` available from Java 11 onwards and offers:

* a **synchronous** backend: ``HttpClientSyncBackend``
* an **asynchronous**, ``Future``-based backend: ``HttpClientFutureBackend``
* an **asynchronous**, ``Monix-Task``-based backend: ``HttpClientMonixBackend`` with streaming support

Websockets
----------

The HttpClient backend supports websockets, where the websocket handler is of type ``sttp.client.httpclient.WebSocketHandler``. An instance of this handler can be created in two ways.

First (the "low-level" one), given an HttpClient-native ``java.net.http.WebSocket.Listener``, you can lift it to a web socket handler using ``WebSocketHandler.fromListener``. This listener will receive lifecycle callbacks, as well as a callback each time a message is received. Note that the callbacks will be executed on the Netty (network) thread, so make sure not to run any blocking operations there, and delegate to other executors/thread pools if necessary. The value returned in the ``WebSocketResponse`` will be an instance of ``java.net.http.WebSocket``, which allows sending messages.

The second, "high-level" approach, available when using the Monix variant, is to pass a ``MonixWebSocketHandler()``. This will create a websocket handler and expose a ``sttp.client.ws.WebSocket[Task]`` interface for sending/receiving messages.

See :ref:`websockets <websockets>` for details on how to use the high-level interface.
