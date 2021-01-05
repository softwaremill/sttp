# Model classes

[sttp model](https://github.com/softwaremill/sttp-model) is a stand-alone project which provides a basic HTTP model, along with constants for common HTTP header names, media types, and status codes.

The basic model classes are: `Header`, `Cookie`, `CookieWithMeta`, `MediaType`, `Method`, `StatusCode` and `Uri`. The `.toString` methods of these classes returns a representation as in a HTTP request/response. See the ScalaDoc for more information.

Companion objects provide methods to construct model class instances, following these rules:

* `.parse(serialized: String): Either[String, ModelClass]`: returns an error message, or an instance of the model class
* `.unsafeParse(serialized: String): Sth`: returns an instance of the model class or in case of an error, throws an exception.
* `.unsafeApply(values)`: creates an instance of the model class; validates the input values and in case of an error, throws an exception. An error could be e.g. that the input values contain characters outside the allowed range
* `.safeApply(...): Either[String, ModelClass]`: same as above, but doesn't throw exceptions. Instead, returns an error message, or the model class instance
* `.apply(...): ModelClass`: creates the model type, without validation, and without throwing exceptions

Moreover, companion objects provide constants and/or constructor methods for well-know model class instances. For example, there's `StatusCode.Ok`, `Method.POST`, `MediaType.ImageGif` and `Header.contentType(MediaType)`.

These constants are also available as traits: `StatusCodes`, `MediaTypes` and `HeaderNames`.

The model also contains aggregate/helper classes such as `Headers` and `MultiQueryParams`.

Example with objects:

```scala
import sttp.client3._
import sttp.model._

object Example {
  val request = basicRequest.header(Header.contentType(MediaType.ApplicationJson))
    .get(uri"https://httpbin.org")

  val backend = HttpURLConnectionBackend()
  val response = request.send(backend)
  if (response.code == StatusCode.Ok) println("Ok!")
}
```

Example with traits:

```scala
import sttp.client3._
import sttp.model._

object Example extends HeaderNames with MediaTypes with StatusCodes {
  val request = basicRequest.header(ContentType, ApplicationJson.toString)
    .get(uri"https://httpbin.org")

  val backend = HttpURLConnectionBackend()
  val response = request.send(backend)
  if (response.code == Ok) println("Ok!")
}
```

For more information see

* [Wikipedia: list of http header fields](https://en.wikipedia.org/wiki/List_of_HTTP_header_fields)
* [Wikipedia: media type](https://en.wikipedia.org/wiki/Media_type)
* [Wikipedia: list of http status codes](https://en.wikipedia.org/wiki/List_of_HTTP_status_codes)
