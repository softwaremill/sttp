Responses
=========

Responses are represented as instances of the case class ``Response[T]``, where ``T`` is the type of the response body. When sending a request, the response will be returned in a wrapper. For example, for asynchronous backends, we can get a ``Future[Response[T]]``, while for the default synchronous backend, the wrapper will be a no-op, ``Id``, which is the same as no wrapper at all.

If sending the request fails, either due to client or connection errors, an exception will be thrown (synchronous backends), or an error will be represented in the wrapper (e.g. a failed future).

.. note::

  If the request completes, but results in a non-2xx return code, the request is still considered successful, that is, a ``Response[T]`` will be returned. See :ref:`response body specifications <responsebodyspec>` for details on how such cases are handled.

Response code
-------------

The response code is available through the ``.code`` property. There are also methods such as ``.isSuccess`` or ``.isServerError`` for checking specific response code ranges.

Response headers
----------------

Response headers are available through the ``.headers`` property, which gives all headers as a sequence (not as a map, as there can be multiple headers with the same name).

Individual headers can be obtained using the methods::

  def header(h: String): Option[String]
  def headers(h: String): Seq[String]

There are also helper methods available to read some commonly accessed headers::

  def contentType: Option[String]
  def contentLength: Option[Long]

Finally, it's possible to parse the response cookies into a sequence of the ``Cookie`` case class::

  def cookies: Seq[Cookie]

If the cookies from a response should be set without changes on the request, this can be done directly; see the :ref:`cookies <cookies>` section in the request definition documentation.

Obtaining the response body
---------------------------

The response body can be obtained through the ``.body`` property, which has type ``Either[String, T]``. ``T`` is the body deserialized as specified in the request - see the next section on :ref:`response body specifications <responsebodyspec>`.

The response body is an either as the body can only be deserialized if the server responded with a success code (2xx). Otherwise, the response body is most probably an error message.

Hence, the ``response.body`` will be a:

* ``Left(errorMessage)`` if the request is successful, but response code is not 2xx.
* ``Right(deserializedBody``) if the request is successful and the response code is 2xx.

You can also forcibly get the deserialized body, regardless of the response code and risking an exception being thrown, using the ``response.unsafeBody`` method.
