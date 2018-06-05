Fetch backend
=============

A Scala Native backend implemented using the `Curl <https://github.com/curl/curl/blob/master/include/curl/curl.h>`_.

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %%% "core" % "1.2.0-RC1"

And add one of implicit values::

  implicit val sttpBackend = CurlBackend()

