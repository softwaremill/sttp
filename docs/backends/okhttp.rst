OkHttp backend
==============

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "okhttp-backend" % "2.0.0-M6"
  // or, for the monix version:
  "com.softwaremill.sttp.client" %% "okhttp-backend-monix" % "2.0.0-M6"

This backend depends on `OkHttp <http://square.github.io/okhttp/>`_, and offers: 

* a **synchronous** backend: ``OkHttpSyncBackend``
* an **asynchronous**, ``Future``-based backend: ``OkHttpFutureBackend``
* an **asynchronous**, Monix-``Task``-based backend: ``OkHttpMonixBackend``

OkHttp fully supports HTTP/2.

Websockets
----------

The OkHttp backend backend support websockets, where the websocket handler is of type ``sttp.client.okhttp.WebSocketHandler``.

An instance of this handler can be created using the ``sttp.client.okhttp.WebSocketHandler(okhttp3.WebSocketListener)`` method. The OkHttp-native listener will receive lifecycle callbacks, as well as a callback each time a message is received. Note that the callbacks will be executed on the OkHttp (network) thread, so make sure not to run any blocking operations there, and delegate to other executors/thread pools if necessary. The value returned in the ``WebSocketResponse`` will be an instance of ``okhttp3.WebSocket``, which allows sending messages.
