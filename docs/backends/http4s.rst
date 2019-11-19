Http4s backend
==============

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "http4s-backend" % "2.0.0-RC2"

This backend depends on `http4s <https://http4s.org>`_ (blaze client), and offers an asynchronous backend, which
can wrap results in any type implementing the `cats-effect <https://github.com/typelevel/cats-effect>`_ ``Effect``
typeclass.

Please note that:
* the backend does not support ``SttpBackendOptions``, that is specifying proxy settings (proxies are not implemented
in http4s, see `this issue <https://github.com/http4s/http4s/issues/251>`_), as well as configuring the connect timeout
* the backend does not support the ``RequestT.options.readTimeout`` option

Instead, all custom timeout configuration should be done by creating a ``org.http4s.client.Client[F]``, using
``org.http4s.client.blaze.BlazeClientBuilder[F]`` and passing it to the appropriate method of the
``Http4sBackend`` object.

The backend supports streaming using fs2. For usage details, see the documentation on `streaming using fs2
with the async-http-backend <asynchttpclient.html#streaming-using-fs2>`_.

