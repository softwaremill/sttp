Headers
=======

Arbitrary headers can be set on the request using the ``.header`` method::

  basicRequest.header("User-Agent", "myapp")

As with any other request definition modifier, this method will yield a new request, which has the given header set. The headers can be set at any point when defining the request, arbitrarily interleaved with other modifiers.

While most headers should be set only once on a request, HTTP allows setting a header multiple times. That's why the ``header`` method has an additional optional boolean parameter, ``replaceExisting``, which defaults to ``true``. This way, if the same header is specified twice, only the last value will be included in the request. If previous values should be preserved, set this parameter to ``false``.

There are also variants of this method accepting a number of headers::

  def header(k: String, v: String, replaceExisting: Boolean = false)
  def headers(hs: Map[String, String])
  def headers(hs: (String, String)*)

Both of the ``headers`` append the given headers to the ones currently in the request, without removing duplicates.

Common headers
--------------

For some common headers, dedicated methods are provided::

  def contentType(ct: String)
  def contentType(ct: String, encoding: String)
  def contentLength(l: Long)
  def acceptEncoding(encoding: String)

See also documentation on setting :ref:`cookies <cookies>` and :ref:`authentication <authentication>`.
