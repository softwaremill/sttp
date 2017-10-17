Redirects
=========

By default, sttp follows redirects.

If you'd like to disable following redirects, use the ``followRedirects`` method::

  sttp.followRedirects(false)

If a request has been redirected, the history of all followed redirects is accessible through the ``response.history`` list. The first response (oldest) comes first. The body of each response will be a ``Left(message)`` (as the status code is non-2xx), where the message is whatever the server returned as the response body.
