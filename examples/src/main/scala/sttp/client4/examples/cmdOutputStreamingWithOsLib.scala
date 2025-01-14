package sttp.client4.examples

import sttp.client4.*
import os.*

private val backend: SyncBackend = DefaultSyncBackend()
private val path: os.Path = os.Path("/tmp/example-file.txt")

@main def cmdOutputStreamingWithOsLib(): Unit = {
  os.remove(path)
  os.write(path, "CONTENT OF THE SIMPLE FILE USED IN THIS EXAMPLE")
  val process = os.proc("cat", path.toString).spawn()
  val request = basicRequest
    .post(uri"http://httpbin.org/post")
    .body(process.stdout.wrapped)
    .response(asString)
  val response = request.send(backend)
  println(response)
}
