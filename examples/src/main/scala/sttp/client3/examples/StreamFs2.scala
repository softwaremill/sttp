package sttp.client3.examples

//import sttp.client3._
//import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
//import cats.effect.{Blocker, ContextShift, IO}
//import cats.instances.string._
//import fs2.{Stream, text}
//import sttp.capabilities.fs2.Fs2Streams
//
//import scala.concurrent.ExecutionContext.global

object StreamFs2 extends App {
  // first needs monix to use cats-effect 3.x
//  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
//
//  def streamRequestBody(backend: SttpBackend[IO, Fs2Streams[IO]]): IO[Unit] = {
//    val stream: Stream[IO, Byte] = Stream.emits("Hello, world".getBytes)
//
//    basicRequest
//      .streamBody(Fs2Streams[IO])(stream)
//      .post(uri"https://httpbin.org/post")
//      .send(backend)
//      .map { response => println(s"RECEIVED:\n${response.body}") }
//  }
//
//  def streamResponseBody(backend: SttpBackend[IO, Fs2Streams[IO]]): IO[Unit] = {
//    basicRequest
//      .body("I want a stream!")
//      .post(uri"https://httpbin.org/post")
//      .response(asStreamAlways(Fs2Streams[IO])(_.chunks.through(text.utf8DecodeC).compile.foldMonoid))
//      .send(backend)
//      .map { response => println(s"RECEIVED:\n${response.body}") }
//  }
//
//  val effect = AsyncHttpClientFs2Backend.resource[IO](Blocker.liftExecutionContext(global)).use { backend =>
//    streamRequestBody(backend).flatMap(_ => streamResponseBody(backend))
//  }
//
//  effect.unsafeRunSync()
}
