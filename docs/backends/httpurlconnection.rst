``HttpURLConnectionBackend``
============================

The default **synchronous** backend. Sending a request returns a response wrapped in the identity type constructor, which is equivalent to no wrapper at all.
 
To use, add an implicit value::

  implicit val sttpBackend = HttpURLConnectionBackend()
