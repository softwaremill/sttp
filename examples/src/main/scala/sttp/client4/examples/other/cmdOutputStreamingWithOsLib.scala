// {cat=Other; effects=Direct; backend=HttpClient}: Command output streaming with os-lib support

//> using dep com.lihaoyi::os-lib:0.11.3
//> using dep com.softwaremill.sttp.client4::core:4.0.6

package sttp.client4.examples.other

import sttp.client4.*
import os.*

private val backend: SyncBackend = DefaultSyncBackend()
private val path: os.Path = os.Path("/tmp/example-file.txt")

@main def cmdOutputStreamingWithOsLib(): Unit = {
  val _ = os.remove(path)
  os.write(path, "CONTENT OF THE SIMPLE FILE USED IN THIS EXAMPLE")
  val process = os.proc("cat", path.toString).spawn()
  val request = basicRequest
    .post(uri"http://httpbin.org/post")
    .body(process.stdout.wrapped)
    .response(asString)
  val response = request.send(backend)
  println(response)
}
