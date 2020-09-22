# Curl backend

A Scala Native backend implemented using [Curl](https://github.com/curl/curl/blob/master/include/curl/curl.h).

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %%% "core" % "2.2.9"
```

and initialize one of the backends:

```scala
implicit val sttpBackend = CurlBackend()
implicit val sttpTryBackend = CurlTryBackend()
```

You need to have an environment with Scala Native [setup](https://scala-native.readthedocs.io/en/latest/user/setup.html)
with additionally installed `libcrypto` (included in OpenSSL) and `curl` in version `7.56.0` or newer.
