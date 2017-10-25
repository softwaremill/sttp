.. _cookies:

Cookies
=======

Cookies sent in requests are key-value pairs contained in the ``Cookie`` header. They can be set on a request in a couple of ways. The first is using the ``.cookie(name: String, value: String)`` method. This will yield a new request definition which, when sent, will contain the given cookie.

Cookies can also be set using the following methods::

  def cookie(nv: (String, String))
  def cookie(n: String, v: String)
  def cookies(nvs: (String, String)*)

Cookies from responses
----------------------

It is often necessary to copy cookies from a response, e.g. after a login request is sent, and a successful response with the authentication cookie received. Having an object ``response: Response[_]``, cookies on a request can be copied::

  // Method signature
  def cookies(r: Response[_])

  // Usage
  sttp.cookies(response)

Or, it's also possible to store only the ``com.softwaremill.sttp.Cookie`` objects (a sequence of which can be obtained from a response), and set the on the request::

  def cookies(cs: Seq[Cookie])

