// {cat=Streaming; effects=ZIO; backend=HttpClient}: Stream request & response bodies using ZIO-Streams

//> using dep com.softwaremill.sttp.client4::zio:4.0.13

package sttp.client4.examples.streaming

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.httpclient.zio.SttpClient
import sttp.client4.httpclient.zio.send
import zio.*
import zio.Console.*
import zio.stream.*

object StreamZio extends ZIOAppDefault:
  def streamRequestBody: RIO[SttpClient, Unit] =
    val stream: Stream[Throwable, Byte] = ZStream("Hello, world".getBytes.toIndexedSeq*)
    send(
      basicRequest
        .post(uri"https://httpbin.org/post")
        .streamBody(ZioStreams)(stream)
    ).flatMap(response => printLine(s"RECEIVED:\n${response.body}"))

  def streamResponseBody: RIO[SttpClient, Unit] =
    send(
      basicRequest
        .post(uri"https://httpbin.org/post")
        .body("I want a stream!")
        .response(asStreamAlways(ZioStreams)(_.via(ZPipeline.utf8Decode).runFold("")(_ + _)))
    ).flatMap(response => printLine(s"RECEIVED:\n${response.body}"))

  override def run =
    (streamRequestBody *> streamResponseBody).provide(HttpClientZioBackend.layer())
