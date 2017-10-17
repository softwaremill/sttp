Request tagging
===============

Each request contains a ``tags: Map[String, Any]`` map. This map can be used to tag the request with any backend-specific information, and isn't used in any way by sttp itself.

Tags can be added to a request using the ``def tag(k: String, v: Any)`` method, and read using the ``def tag(k: String): Option[Any]`` method.

Backends, or :ref:`backend wrappers <custombackends>` can use tags e.g. for logging, passing a metric name, using different connection pools, or even different delegate backends.

