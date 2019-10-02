OkHttp backend
==============

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "okhttp-backend" % "2.0.0-M5"
  // or, for the monix version:
  "com.softwaremill.sttp.client" %% "okhttp-backend-monix" % "2.0.0-M5"

This backend depends on `OkHttp <http://square.github.io/okhttp/>`_, and offers: 

* a **synchronous** backend: ``OkHttpSyncBackend``
* an **asynchronous**, ``Future``-based backend: ``OkHttpFutureBackend``
* an **asynchronous**, Monix-``Task``-based backend: ``OkHttpMonixBackend``

OkHttp fully supports HTTP/2.

