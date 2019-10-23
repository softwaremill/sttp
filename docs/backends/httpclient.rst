HttpClient backend (Java 11+)
=============================

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "httpclient-backend" % "2.0.0-M7"

This backend is based on ``java.net.http.HttpClient`` available from Java 11 onwards and offers:

* a **synchronous** backend: ``HttpClientSyncBackend``
* an **asynchronous**, ``Future``-based backend: ``HttpClientFutureBackend``

Websockets
----------

The HttpClient backend backend support websockets, where the websocket handler is of type ``sttp.client.httpclient.WebSocketHandler``.

An instance of this handler can be created using the ``sttp.client.httpclient.WebSocketHandler(java.net.http.WebSocket.Listener)`` method. The Java-native listener will receive lifecycle callbacks, as well as a callback each time a message is received. Note that the callbacks will be executed on the network thread, so make sure not to run any blocking operations there, and delegate to other executors/thread pools if necessary. The value returned in the ``WebSocketResponse`` will be an instance of ``java.net.http.WebSocket``, which allows sending messages.
