.. _responsebodyspec:

Response body specification
===========================

By default, the received response body will be read as a ``String``, using the encoding specified in the ``Content-Type`` response header (and if none is specified, using ``UTF-8``). This is of course configurable: response bodies can be ignored, deserialized into custom types, received as a stream or saved to a file.

How the response body will be read is part of the request definition, as already when sending the request, the backend needs to know what to do with the response. The type to which the response body should be deserialized is the second type parameter of ``RequestT``, and stored in the request definition as the ``request.response: ResponseAs[T, S]`` property.

Basic response specifications
-----------------------------

To conveniently specify how to deserialize the response body, a number of ``asXxx`` methods are available. They can be used to provide a value for the request definition's ``response`` modifier::

  sttp.response(asByteArray)

When the above request is completed and sent, it will result in a ``Response[Array[Byte]]``. Other possible response specifications are::

  def ignore: ResponseAs[Unit, Nothing]
  def asString: ResponseAs[String, Nothing]
  def asString(encoding: String): ResponseAs[String, Nothing]
  def asByteArray: ResponseAs[Array[Byte], Nothing]
  def asParams: ResponseAs[Seq[(String, String)], Nothing]
  def asParams(encoding: String): ResponseAs[Seq[(String, String)], Nothing] =
  def asFile(file: File, overwrite: Boolean = false): ResponseAs[File, Nothing]
  def asPath(path: Path, overwrite: Boolean = false): ResponseAs[Path, Nothing]

Hence, to discard the response body, simply specify::

  sttp.response(ignore)

And to save the response to a file::

  sttp.response(asFile(someFile))

.. note::

  As the handling of response is specified upfront, there's no need to "consume" the response body. It can be safely discarded if not needed.

.. _responsebodyspec_handlenon2xx:

Handling non 2xx responses
--------------------------

By default only a successful (2xx) response body can be obtained. To customize this behaviour use ``.parseResponseIf`` method::

  val response =
    sttp
      .post(uri"...")
      .body(requestPayload)
      .response(asXxx)
      .parseResponseIf(status => status == 400 || status == 200)
      .send()


.. _responsebodyspec_custom:

Custom body deserializers
-------------------------

It's possible to define custom body deserializers by taking any of the built-in response specifications and mapping over them. Each ``ResponseAs`` instance has ``map`` and ``mapWithMetadata`` methods, which can be used to transform it to a specification for another type (optionally using response metadata, such as headers or the status code). Each such value is immutable and can be used multiple times.

As an example, to read the response body as an int, the following response specification can be defined (warning: this ignores the possibility of exceptions!)::

  val asInt: ResponseAs[Int, Nothing] = asString.map(_.toInt)
  
  sttp
    .response(asInt)
    ...

To integrate with a third-party JSON library::

  def parseJson(json: String): Either[JsonError, JsonAST] = ...
  val asJson: ResponseAs[Either[JsonError, JsonAST], Nothing] = asString.map(parseJson)
  
  sttp
    .response(asJson)
    ...
  
For some mapped response specifications available out-of-the-box, see :ref:`json support <json>`.

Streaming
---------

If the backend used supports streaming (see :ref:`backends summary <backends_summary>`), it's possible to receive responses as a stream. This can be specified using the following method::

  def asStream[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()

For example, when using the :ref:`akka-http backend <akkahttp>`::

  import sttp.client._
  import sttp.client.akkahttp._
  
  import akka.stream.scaladsl.Source
  import akka.util.ByteString
  
  implicit val sttpBackend = AkkaHttpBackend() 
  
  val response: Future[Response[Source[ByteString, Any]]] = 
    sttp
      .post(uri"...")
      .response(asStream[Source[ByteString, Any]])
      .send()

.. note::    

  Unlike with non-streaming response handlers, each streaming response should be entirely consumed by client code.

