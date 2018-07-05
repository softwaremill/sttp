Curl backend
=============

A Scala Native backend implemented using `Curl <https://github.com/curl/curl/blob/master/include/curl/curl.h>`_.

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %%% "core" % SttpCoreVersion

and initialize one of the backends::

  implicit val sttpBackend = CurlBackend()
  implicit val sttpTryBackend = CurlTryBackend()

