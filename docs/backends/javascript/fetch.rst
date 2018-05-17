fetch backend
=================

The default JavaScript backend implemented using the Fetch API_.

Timeouts are handled via the experimental AbortController_ API. As this API only recently appeared in browsers you can disable request timeout by passing in ``FetchOptions`` with ``enableTimeouts`` set to false.

To use, add an implicit value::

  implicit val sttpBackend = FetchBackend()


.. _Fetch API: https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API
.. _AbortController: https://developer.mozilla.org/en-US/docs/Web/API/AbortController
