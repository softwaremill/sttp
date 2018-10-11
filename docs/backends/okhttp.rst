OkHttp backend
==============

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %% "okhttp-backend" % "1.3.7"
  // or, for the monix version:
  "com.softwaremill.sttp" %% "okhttp-backend-monix" % "1.3.7"

This backend depends on `OkHttp <http://square.github.io/okhttp/>`_, and offers: 

* a **synchronous** backend: ``OkHttpSyncBackend``
* an **asynchronous**, ``Future``-based backend: ``OkHttpFutureBackend``
* an **asynchronous**, Monix-``Task``-based backend: ``OkHttpMonixBackend``

OkHttp fully supports HTTP/2.

