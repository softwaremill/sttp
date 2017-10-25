# sttp

[![Join the chat at https://gitter.im/softwaremill/sttp](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/softwaremill/sttp?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/softwaremill/sttp.svg?branch=master)](https://travis-ci.org/softwaremill/sttp)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp/core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp/core_2.12)
[![Dependencies](https://app.updateimpact.com/badge/634276070333485056/sttp.svg?config=compile)](https://app.updateimpact.com/latest/634276070333485056/sttp)

The Scala HTTP client that you always wanted!

sttp is an open-source library which provides a clean, programmer-friendly API to define HTTP requests and execute them using one of the wrapped backends, such as akka-http, async-http-client or OkHttp.
 
```scala
import com.softwaremill.sttp._

val sort: Option[String] = None
val query = "http language:scala"

// the `query` parameter is automatically url-encoded
// `sort` is removed, as the value is not defined
val request = sttp.get(uri"https://api.github.com/search/repositories?q=$query&sort=$sort")
  
implicit val backend = HttpURLConnectionBackend()
val response = request.send()

// response.header(...): Option[String]
println(response.header("Content-Length")) 

// response.unsafeBody: by default read into a String 
println(response.unsafeBody)                     
```

## Documentation

sttp documentation is available at [sttp.readthedocs.io](http://sttp.readthedocs.io).

## Quickstart with Ammonite

If you are an [Ammonite](http://ammonite.io) user, you can quickly start experimenting with sttp by copy-pasting the following:

```scala
import $ivy.`com.softwaremill.sttp::core:1.0.1`
import com.softwaremill.sttp._
implicit val backend = HttpURLConnectionBackend()
sttp.get(uri"http://httpbin.org/ip").send()
```

## Quickstart with sbt

Add the following dependency:

```scala
"com.softwaremill.sttp" %% "core" % "1.0.1"
```

Then, import:

```scala
import com.softwaremill.sttp._
```

Type `sttp.` and see where your IDE’s auto-complete gets you!

## Contributing

If you have a question, or hit a problem, feel free to ask on our [gitter channel](https://gitter.im/softwaremill/sttp)!

Or, if you encounter a bug, something is unclear in the code or documentation, don’t hesitate and open an issue on GitHub.

We are also always looking for contributions and new ideas, so if you’d like to get into the project, check out the [open issues](https://github.com/softwaremill/sttp/issues), or post your own suggestions!
