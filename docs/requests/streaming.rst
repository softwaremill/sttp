.. _streaming:

Streaming
=========

Some backends (see :ref:`backends summary <backends_summary>`) support streaming bodies. If that's the case, you can set a stream of the supported type as a request body using the ``streamBody`` method, instead of the usual ``body`` method.

.. note::

  Here, streaming refers to (usually) non-blocking, asynchronous streams of data. To send data which is available as an ``InputStream``, or a file from local storage (which is available as a ``File`` or ``Path``), no special backend support is needed. See the documenttation on :ref:`setting the request body <requestbody>`.

For example, using the :ref:`akka-http backend <akkahttp>`, a request with a streaming body can be defined as follows::

  import sttp.client._
  import sttp.client.akkahttp._
  
  import akka.stream.scaladsl.Source
  import akka.util.ByteString
  
  val source: Source[ByteString, Any] =   ...
  
  basicRequest
    .streamBody(source)
    .post(uri"...")

.. note::

  A request with the body set as a stream can only be sent using a backend supporting exactly the given type of streams.
