Proxy support
=============

A proxy can be specified when creating a backend::
 
  import com.softwaremill.sttp._
  
  implicit val backend = HttpURLConnectionBackend(
    options = SttpBackendOptions.httpProxy("some.host", 8080))
  
  sttp
    .get(uri"...")
    .send() // uses the proxy

  
