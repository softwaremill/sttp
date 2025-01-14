// {cat=Hello, World!; effects=Direct; backend=HttpClient}: POST form data

//> using dep com.softwaremill.sttp.client4::core:4.0.0-M24

package sttp.client4.examples

import sttp.client4.*

@main def postFormSynchronous(): Unit =
  val signup = Some("yes")

  val request = basicRequest
    // send the body as form data (x-www-form-urlencoded)
    .body(Map("name" -> "John", "surname" -> "doe"))
    // use an optional parameter in the URI
    .post(uri"https://httpbin.org/post?signup=$signup")

  val backend = DefaultSyncBackend()
  val response = request.send(backend)

  // the response body should contain a "form" field with the uploaded form data
  println(response.body)
