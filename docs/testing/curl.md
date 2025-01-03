# Converting requests to CURL commands

sttp comes with builtin request to curl converter. To convert request to curl invocation use `.toCurl` method.

For example:

```scala mdoc
import sttp.client4.*

basicRequest.get(uri"http://httpbin.org/ip").toCurl
```

Note that the `Accept-Encoding` header, which is added by default to all requests (`Accept-Encoding: gzip, deflate`), can make curl warn that _binary output can mess up your terminal_, when running generated command from the command line. It can be omitted by setting `omitAcceptEncoding = true` when calling `.toCurl` method.