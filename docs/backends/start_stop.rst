Starting & cleaning up
======================

In case of most backends, you should only instantiate a backend once per application, as a backend typically allocates resources such as thread or connection pools.

When ending the application, make sure to call ``backend.close()``, which results in an effect which frees up resources used by the backend (if any). If the effect wrapper for the backend is lazily evaluated, make sure to include it when composing effects!

Note that only resources allocated by the backends are freed. For example, if you use the ``AkkaHttpBackend()`` the ``close()`` method will terminate the underlying actor system. However, if you have provided an existing actor system upon backend creation (``AkkaHttpBackend.usingActorSystem``), the ``close()`` method will be a no-op.

