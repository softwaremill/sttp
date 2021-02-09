# Armeria backend

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "armeria-backend" % "@VERSION@"
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http) and provides an asynchronous backend, wrapped in Scala `Future`.
It allows you to provide custom `WebClient` from Armeria's API, otherwise default will be used.

```scala
import sttp.client.armeria.ArmeriaBackend
val backend = ArmeriaBackend()
```

Please note that the backend does not support:

* multipart content
* non-blocking [streaming](../requests/streaming.md) or [websockets](../websockets.md)
* `SttpBackendOptions`, that is specifying proxy settings