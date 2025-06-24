// {cat=Hello, World!; effects=Direct; backend=HttpClient}: Dynamic URI components

//> using dep com.softwaremill.sttp.client4::core:4.0.9

package sttp.client4.examples

import sttp.client4.*

import scala.util.Random

@main def dynamicUriSynchronous(): Unit =
  val dynamicPath = List("Hello", Random().nextInt(100).toString, "World!")
  val dynamicQuery = Map("p1" -> Random().nextInt(100).toString, "p2" -> "special ąęść characters")
  val optionalQuery = if Random().nextBoolean() then Some("yes") else None

  val requestUri = uri"https://httpbin.org/anything/$dynamicPath?$dynamicQuery&optional=$optionalQuery"

  val backend = DefaultSyncBackend()
  val response = basicRequest.get(requestUri).send(backend)

  println("Sending request to: " + requestUri)
  println("Got response:")
  println(response.body)
