.. _custombackends:

Custom backends, logging, metrics
=================================

It is also entirely possible to write custom own backend (if doing so, please consider contributing!) or wrapping an existing one. One can even write completely generic wrappers for any delegate backend, as each backend comes equipped with a monad for the response type. This brings the possibility to ``map`` and ``flatMap`` over responses. 

Possible use-cases for wrapper-backend include:
 
* logging
* capturing metrics
* request signing (transforming the request before sending it to the delegate)

To pass some context to wrapper-backends, requests can be *tagged*. Each ``RequestT`` instance contains a ``tags: Map[String, Any]`` field. This is unused by http, but can be used e.g. to pass a metric name or logging context.

Example backend logging

