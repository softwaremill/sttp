//> using dep com.softwaremill.sttp.client4::core:4.0.0-M22

import sttp.client4.*

@main def sttpDemo(): Unit =
  val sort: Option[String] = None
  val query = "http language:scala"

  // the `query` parameter is automatically url-encoded
  // `sort` is removed, as the value is not defined
  val request = basicRequest.get(uri"https://api.github.com/search/repositories?q=$query&sort=$sort")

  val backend = DefaultSyncBackend()
  val response = request.send(backend)

  // response.header(...): Option[String]
  println(response.header("Content-Length"))

  // response.body: by default read into an Either[String, String] to indicate failure or success
  println(response.body)
