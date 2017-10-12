SSL
===

SSL handling can be customized (or disabled) when creating a backend and is 
backend-specific. 

Depending on the underlying backend's client, you can customize SSL settings
as follows:

* ``HttpUrlConnectionBackend``: when creating the backend, specify the ``customizeConnection: HttpURLConnection => Unit`` parameter, and set the hostname verifier & SSL socket factory as required
* akka-http: when creating the backend, specify the ``customHttpsContext: Option[HttpsConnectionContext]`` parameter. See `akka-http docs <http://doc.akka.io/docs/akka-http/current/scala/http/server-side/server-https-support.html>`_
* async-http-client: create a custom client and use the ``setSSLContext`` method
* OkHttp: create a custom client modifying the SSL settings as described `on the wiki <https://github.com/square/okhttp/wiki/HTTPS>`_

