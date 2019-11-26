.. _quickstart:

Quickstart
==========

The main sttp client API comes in a single jar, with a single transitive dependency on the `sttp model <https://github.com/softwaremill/sttp-model>`_. This also includes a default, synchronous backend, which is based on Java's ``HttpURLConnection``. For production usages, you'll often want to use an alternate backend (but what's important is that the API remains the same!). See the section on :ref:`backends <backends_summary>` for additional instructions.

Using sbt
---------

The basic dependency which provides the API and the default synchronous backend is::

  "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC3"

``sttp client`` is available for Scala 2.11, 2.12 and 2.13, and requires Java 8.

``sttp client`` is also available for Scala.js 0.6 and Scala Native. Note that not all modules are compatible and there are no backends that can be used on both.

Using Ammonite
--------------

If you are an `Ammonite <https://ammonite.io>`_ user, you can quickly start experimenting with sttp by copy-pasting the following::

  import $ivy.`com.softwaremill.sttp.client::core:2.0.0-RC3`
  import sttp.client.quick._
  quickRequest.get(uri"http://httpbin.org/ip").send()

Importing the ``quick`` object has the same effect as importing ``sttp.client._``, plus defining an implicit synchronous backend (``val backend = HttpURLConnectionBackend()``), so that sttp can be used right away.

If the default ``HttpURLConnectionBackend`` for some reason is insufficient, you can also use one based on OkHttp::

  import $ivy.`com.softwaremill.sttp.client::okhttp-backend:2.0.0-RC3`
  import sttp.client.okhttp.quick._
  quickRequest.get(uri"http://httpbin.org/ip").send()

Imports
-------

Working with sttp is most convenient if you import the ``sttp.client`` package entirely::

  import sttp.client._

This brings into scope the starting point for defining requests and some helper methods. All examples in this guide assume that this import is in place.

And that's all you need to start using sttp client! To create and send your first request, import the above, type ``basicRequest.`` and see where your IDE's auto-complete gets you! Or, read on about the :ref:`basics of defining requests <request_basics>`.

