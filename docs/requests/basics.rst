.. _request_basics:

Request definition basics
=========================

As mentioned in the :ref:`quickstart <quickstart>`, the following import will be needed::

  import com.softwaremill.sttp._

This brings into scope ``sttp``, the starting request. This request can be customised, each time yielding a new, immutable request definition (unless a mutable body is set on the request, such as a byte array). As the request definition is immutable, it can be freely stored in values, shared across threads, and customized multiple times in various ways.

For example, we can set a cookie, ``String`` -body and specify that this should be a ``POST`` request to a given URI::

  val request = sttp
      .cookie("login", "me")
      .body("This is a test")
      .post(uri"http://endpoint.com/secret")
  
The request parameters (headers, cookies, body etc.) can be specified **in any order**. It doesn't matter if the request method, the body, the headers or connection options are specified in this sequence or another. This way you can build arbitrary request templates, capturing all that's common among your requests, and customizing as needed. Remember that each time a modifier is applied to a request, you get a new immutable object.

There's a lot of ways in which you can customize a request, which are covered in this guide. Another option is to just explore the API: most of the methods are self-explanatory and carry scaladocs if needed.

Using the modifiers, each time we get a new request definition, but it's just a description: a data object; nothing is sent over the network until the ``send()`` method is invoked.

Sending a request
-----------------

A request definition can be created without knowing how it will be sent. But to send a request, a backend is needed. A default, synchronous backend based on Java's ``HttpURLConnection`` is provided out-of-the box.

To invoke the ``send()`` method on a request description, an implicit value of type ``SttpBackend`` needs to be in scope::

  implicit val backend = HttpURLConnectionBackend()
  
  val response: Response[String] = request.send()

The default backend doesn't wrap the response into any container, but other asynchronous backends might do so. See the section on :ref:`backends <backends_summary>` for more details.

.. note::

  Only requests with the request method and uri can be sent. If trying to send a request without these components specified, a compile-time error will be reported. On how this is implemented, see the documentation on the :ref:`type of request definitions <request_type>`.

Starting requests
-----------------

sttp provides two starting requests:

* ``sttp``, which is an empty request with the ``Accept-Encoding: gzip, deflate`` header added. That's the one that is most commonly used.
* ``empty``, a completely empty request, with no headers at all.

Both of these requests will by default read the response body into a UTF-8 ``String``. How the response body is handled is also part of the request definition. See the section on :ref:`response body specifications <responsebodyspec>` for more details on how to customize that.

Debugging requests
------------------

sttp comes with builtin request to curl converter. To convert request to curl invocation use `toCurl` method.

For example converting given request::

    sttp.get(uri"http://httpbin.org/ip").toCurl

will result in following curl command::

    curl -L --max-redirs 32 -X GET -H "Accept-Encoding: gzip, deflate" "http://httpbin.org/ip"

