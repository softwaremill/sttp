HTTP Constants
==============

sttp provides constants for common HTTP header names, media types, and status codes.

Constants for common header names are provided by the ``HeaderNames`` `trait and object <https://github.com/softwaremill/sttp/blob/master/core/shared/src/main/scala/com/softwaremill/sttp/HeaderNames.scala>`_.

Constants for common media types are provided by the ``MediaTypes`` `trait and object <https://github.com/softwaremill/sttp/blob/master/core/shared/src/main/scala/com/softwaremill/sttp/MediaTypes.scala>`_.

Constants for common status codes are provided by the ``StatusCodes`` `trait and object <https://github.com/softwaremill/sttp/blob/master/core/shared/src/main/scala/com/softwaremill/sttp/StatusCodes.scala>`_.

Example with objects::

  import sttp.client._

  object Example {
    val request = sttp.header(HeaderNames.ContentType, MediaTypes.Json).get(uri"https://httpbin.org")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()
    if (response.code == StatusCodes.Ok) println("Ok!")
  }

Example with traits::

  import sttp.client._

  object Example extends HeaderNames with MediaTypes with StatusCodes {
    val request = sttp.header(ContentType, Json).get(uri"https://httpbin.org")

    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()
    if (response.code == Ok) println("Ok!")
  }


For more information see
 * https://en.wikipedia.org/wiki/List_of_HTTP_header_fields
 * https://en.wikipedia.org/wiki/Media_type
 * https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
