.. _authentication:

Authentication
==============

sttp supports basic, bearer-token based authentication and digest authentication. Two first cases are handled by adding an ``Authorization`` header with the appropriate credentials.

Basic authentication, using which the username and password are encoded using Base64, can be added as follows::

  basicRequest.auth.basic(username, password)

A bearer token can be added using::

  basicRequest.auth.bearer(token)


Digest authentication
---------------------

This type of authentication works differently. In its assumptions it is based on an additional message exchange between client and server.
Due to that a special wrapping backend is need to handle that additional logic.

In order to add digest authentication support just wrap other backend as follows::

  val myBackend: SttpBackend[R, S, WS_HANDLER] = ???
  new DigestAuthenticationBackend(myBackend)

Then only thing which we need to do is to pass our credentials to the relevant request::

  val secureRequest = basicRequest.auth.digest(username, password)

It is also possible to use digest authentication against proxy::

  val secureProxyRequest = basicRequest.proxyAuth.digest(username, password)

Both of above methods can be combined with different values if proxy and target server use digest authentication.

To learn more about digest authentication visit `wikipedia <https://en.wikipedia.org/wiki/Digest_access_authentication>`_

Also keep in mind that there are some limitations with the current implementation:

* there is no caching so each request will result in an additional round-trip (or two in case of proxy and server)
* authorizationInfo is not supported
* scalajs supports only md5 algorithm
* it doesn't work in scala-native
