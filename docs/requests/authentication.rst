.. _authentication:

Authentication
==============

sttp supports basic and bearer-token based authentication. In both cases, an ``Authorization`` header is added with the appropriate credentials.

Basic authentication, using which the username and password are encoded using Base64, can be added as follows::

  sttp.auth.basic(username, password)

A bearer token can be added using::

  sttp.auth.bearer(token)

