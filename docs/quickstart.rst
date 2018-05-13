.. _quickstart:

Quickstart
==========

The main sttp API comes in a single jar without transitive dependencies. This also includes a default, synchronous backend, which is based on Java's ``HttpURLConnection``. For production usages, you'll often want to use an alternate backend (but what's important is that the API remains the same!). See the section on :ref:`backends <backends_summary>` for additional instructions.

Using sbt
---------

The basic dependency which provides the API and the default synchronous backend is::

  "com.softwaremill.sttp" %% "core" % "1.1.14"

``sttp`` is available for Scala 2.11 and 2.12, and requires Java 8. The core module has no transitive dependencies.

Using Ammonite
--------------

If you are an `Ammonite <http://ammonite.io>`_ user, you can quickly start experimenting with sttp by copy-pasting the following::

  import $ivy.`com.softwaremill.sttp::core:1.1.14`
  import com.softwaremill.sttp._
  implicit val backend = HttpURLConnectionBackend()
  sttp.get(uri"http://httpbin.org/ip").send()

Imports
-------

Working with sttp is most convenient if you import the ``sttp`` package entirely::

  import com.softwaremill.sttp._

This brings into scope the starting point for defining requests and some helper methods. All examples in this guide assume that this import is in place.

And that's all you need to start using sttp! To create and send your first request, import the above, type ``sttp.`` and see where your IDE's auto-complete gets you! Or, read on about the :ref:`basics of defining requests <request_basics>`.

