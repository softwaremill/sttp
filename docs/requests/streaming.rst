.. _streaming:

Streaming
=========

Some backends (see :ref:`backends summary <backends_summary>`) support streaming bodies. If that's the case, you can set a stream of the supported type as a request body using the ``streamBody`` method.

For example, using the :ref:`akka-http backend <akkahttp>`, a request with a stream body can be defined as follows::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.akkahttp._
  
  import akka.stream.scaladsl.Source
  import akka.util.ByteString
  
  val source: Source[ByteString, Any] =   ...
  
  sttp
    .streamBody(source)
    .post(uri"...")

.. note::

  A request with the body set as a stream can only be sent using a backend supporting exactly the given type of streams.
