# Logging 

The `sttp.client.logging.LoggingBackend` can log requests and responses which end successfully or with an exception. It can be created given:

* a `sttp.client.logging.Logger`, which is an integration point with logging libraries. Two such integration that are available with sttp-client is slf4j and scribe (see below), but custom ones can be easily added.
* a `sttp.client.logging.Log`, which constructs messages and performs logging actions. A custom implementation can be provided to change default log levels or log message content.

By default, the following options are exposed:

* `includeTiming` - should the duration of the request be included in the log message
* `beforeCurlInsteadOfShow` - before sending a request, instead of a summary of the request to be sent, log the curl command which corresponds to the request
* `logRequestBody` - should the request body be logged before sending the request (if the request body can be logged)
* `logResponseBody` - should the response body be logged after receiving a response to the request (if the response body can be replayed)  

The messages are by default logged on these levels:

* `DEBUG` before the request is sent
* `DEBUG` when a request completes successfully
* `ERROR` when there's an exception when sending a request

## Using slf4j

To use the [slf4j](http://www.slf4j.org) logging backend wrapper, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "slf4j-backend" % "@VERSION@"
``` 

There are three backend wrappers available, which log request & response information using a slf4j `Logger`. To see the logs, you'll need to use an slf4j-compatible logger implementation, e.g.  [logback](http://logback.qos.ch), or use a binding, e.g. [log4j-slf4j](https://logging.apache.org/log4j/2.0/log4j-slf4j-impl/index.html).

Example usage:

```scala mdoc:compile-only
import sttp.client._
import sttp.client.logging.slf4j.Slf4jLoggingBackend

val backend = Slf4jLoggingBackend(HttpURLConnectionBackend())
basicRequest.get(uri"https://httpbin.org/get").send(backend)

// Logs:
// 21:14:23.735 [main] INFO sttp.client.logging.slf4j.Slf4jTimingBackend - Request: GET https://httpbin.org/get, took: 0.795s, response: 200
```

To create a customised logging backend, see the section on [custom backends](custom.md).

## Using scribe

To use the [scribe](https://github.com/outr/scribe) logging backend wrapper, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "scribe-backend" % "@VERSION@"
``` 