Timeouts
========

Sttp supports read and connection timeouts: 

* Connection timeout - can be set globally (30 seconds by default)
* Read timeout - can be set per request (1 minute by default)

How to use::

  import com.softwaremill.sttp._
  import scala.concurrent.duration._
  
  // all backends provide a constructor that allows users to specify backend options
  implicit val backend = HttpURLConnectionBackend(
    options = SttpBackendOptions.connectionTimeout(1.minute))
  
  sttp
    .get(uri"...")
    .readTimeout(5.minutes) // or Duration.Inf to turn read timeout off
    .send()

  
