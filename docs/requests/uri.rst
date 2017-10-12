Defining requests: URI Interpolator
===================================

Using the URI interpolator it's possible to conveniently create ``Uri` instances, which can then be used to specify request endpoints, for example::

  import com.softwaremill.sttp._
  
  val user = "Mary Smith"
  val filter = "programming languages"
  
  val endpoint: Uri = uri"http://example.com/$user/skills?filter=$filter"

Any values embedded in the URI will be URL-encoded, taking into account the 
context (e.g., the whitespace in ``user`` will be %-encoded as ``%20D``, while the
whitespace in ``filter`` will be query-encoded as ``+``). 

The possibilities of the interpolator don't end here. Other supported features:

* parameters can have optional values: if the value of a parameter is ``None``, it will be removed
* maps, sequences of tuples and sequences of values can be embedded in the query part. They will be expanded into query parameters. Maps and sequences of tuples can also contain optional values, for which mappings will be removed if ``None``.
* optional values in the host part will be expanded to a subdomain if ``Some``, removed if ``None``
* sequences in the host part will be expanded to a subdomain sequence
* if a string containing the protocol is embedded *as the very beginning*, it will not be escaped, allowing to embed entire addresses as prefixes, e.g.: ``uri"$endpoint/login"``, where ``val endpoint = "http://example.com/api"``.
 
A fully-featured example::

  import com.softwaremill.sttp._
  val secure = true
  val scheme = if (secure) "https" else "http"
  val subdomains = List("sub1", "sub2")
  val vx = Some("y z")
  val params = Map("a" -> 1, "b" -> 2)
  val jumpTo = Some("section2")
  uri"$scheme://$subdomains.example.com?x=$vx&$params#$jumpTo"
  
  // generates:
  // https://sub1.sub2.example.com?x=y+z&a=1&b=2#section2
