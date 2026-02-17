// {cat=Streaming; effects=Direct; backend=HttpClient}: Stream request & response bodies using Ox's Flow (synchronous, blocking streams)

//> using dep com.softwaremill.sttp.client4::ox:4.0.18

package sttp.client4.examples.ws

import ox.*
import ox.flow.Flow
import sttp.client4.*

@main def streamOx: Unit =
  val backend = DefaultSyncBackend()
  supervised:
    releaseAfterScope(backend.close())

    val result = basicRequest
      .post(uri"https://httpbin.org/post")
      .body(
        Flow.fromValues(Chunk.fromArray("Hello, ".getBytes()), Chunk.fromArray("world!".getBytes())).runToInputStream()
      )
      .response(asInputStream(is => Flow.fromInputStream(is).decodeStringUtf8.runToList().mkString))
      .send(backend)

    println(s"Received: ${result.body}")
