Defining requests: basics
=========================

To get easy access to the request definition API, add the following import::

  import com.softwaremill.sttp._

This brings into scope ``sttp``, the starting request (it's an empty request
with the ``Accept-Encoding: gzip, defalte`` header added). This request can 
be customised, each time yielding a new, immutable request description 
(unless a mutable body is set on the request, such as a byte array).

For example, we can set a cookie, string-body and specify that this should
be a ``POST`` request to a given URI::

  val request = sttp
      .cookie("login", "me")
      .body("This is a test")
      .post(uri"http://endpoint.com/secret")

The request parameters (headers, cookies, body etc.) can be specified in any
order. There's a lot of ways in which you can customize a request: just
explore the API.

You can create a request description without knowing how it will be sent.
But to send a request, you will need a backend. A default, synchronous backend
based on Java's ``HttpURLConnection`` is provided out-of-the box. An implicit 
value of type ``SttpBackend`` needs to be in scope to invoke the ``send()`` on the
request::

  implicit val backend = HttpURLConnectionBackend()
  
  val response: Response[String] = request.send()

By default the response body is read into a utf-8 string. How the response body
is handled is also part of the request description. The body can be ignore
(``.response(ignore)``), read into a sequence of parameters 
(``.response(asParams)``), mapped (``.mapResponse``) and more; some backends also 
support request & response streaming.

The default backend doesn't wrap the response into any container, but other
asynchronous backends might do so. The type parameter in the ``Response[_]``
type specifies the type of the body.
