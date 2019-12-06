finagle backend
=================

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "finagle-backend" % "2.0.0-RC5"

This backend depends on `finagle <https://twitter.github.io/finagle//>`. Sending a request returns a response wrapped in a ``com.twitter.util.Future``.

Add an implicit value using finagle backend::

  implicit val sttpBackend = FinagleBackend()

  // or, if you'd like to use an existing Finagle `client <https://twitter.github.io/finagle/guide/Clients.html>``:
  implicit val sttpBackend = FinagleBackend.usingClient(client)