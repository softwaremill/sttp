.. _streaming:

Websockets
==========

Apart from streaming, backends (see :ref:`backends summary <backends_summary>`) can also optionally support websockets. Websocket requests are described exactly the same as regular requests, starting with ``basicRequest``, adding headers, specifying the request method and uri.

The difference is that instead of calling ``send()``, ``openWebsocket(handler)`` should be called instead, given an instance of a backend-specific websocket handler. Refer to documentation of individual backends for details on how to instantiate the handler.

After opening a websocket, a ``WebSocketResponse`` instance is returned, wrapped in a backend-specific effects wrapper. If the protocol upgrade hasn't been successfull, the request will fail with an error (represented as an exception or a failed effects wrapper).

In case of success, ``WebSocketResponse`` contains:

* the headers returned when opening the websocket
* a handler and backend-specific value
