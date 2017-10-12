sttp: the Scala HTTP client you always wanted!
==============================================

`sttp <https://github.com/softwaremill/sttp>`_ is an open-source library which provides a clean, programmer-friendly API to define HTTP requests and execute them using one of the wrapped backends, such as `akka-http <https://doc.akka.io/docs/akka-http/current/scala/http/>`_, `async-http-client <https://github.com/AsyncHttpClient/async-http-client>`_ or `OkHttp <http://square.github.io/okhttp/>`_.

First impressions
-----------------

.. code-block:: scala
   
  import com.softwaremill.sttp._
  
  val sort: Option[String] = None
  val query = "http language:scala"
  
  // the `query` parameter is automatically url-encoded
  // `sort` is removed, as the value is not defined
  val request = sttp.get(uri"https://api.github.com/search/repositories?q=$query&sort=$sort")
    
  implicit val backend = HttpURLConnectionBackend()
  val response = request.send()
  
  // response.header(...): Option[String]
  println(response.header("Content-Length")) 
  
  // response.unsafeBody: by default read into a String 
  println(response.unsafeBody)                     
 

Quickstart with Ammonite
------------------------

If you are an `Ammonite <http://ammonite.io>`_ user, you can quickly start 
experimenting with sttp by copy-pasting the following::

  import $ivy.`com.softwaremill.sttp::core:0.0.20`
  import com.softwaremill.sttp._
  implicit val backend = HttpURLConnectionBackend()
  sttp.get(uri"http://httpbin.org/ip").send()

Adding sttp to your project
---------------------------

The basic dependency which provides the default, synchornous backend is::

  "com.softwaremill.sttp" %% "core" % "0.0.20"

``sttp`` is available for Scala 2.11 and 2.12, and requires Java 7 if using an ``OkHttp`` based backend, or Java 8 otherwise. The core module has no transitive dependencies.

If you'd like to use an alternate backend, [see below](#supported-backends) for additional instructions.

.. toctree::
   :maxdepth: 2
      
   goals
   requests/basics
   requests/uri
   requests/defaults           
   requests/type
   backends/summary
   backends/start_stop
   backends/httpurlconnection
   backends/akkahttp
   backends/asynchttpclient
   backends/okhttp
   backends/custom
   conf/timeouts
   conf/ssl
   conf/proxy           
   json           
   testing
   other           
   credits           
             
Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`
