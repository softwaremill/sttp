// {cat=Other; effects=Direct; backend=HttpClient}: Download file with os-lib support

//> using dep com.lihaoyi::os-lib:0.11.3
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC1

package sttp.client4.examples.other

import sttp.client4.*
import os.*

private val path: os.Path = os.Path("/tmp/example-file.txt")
private val backend: SyncBackend = DefaultSyncBackend()

@main def uploadFileWithOsLib(): Unit = {
  val _ = os.remove(path)
  os.write(path, "THIS IS CONTENT OF TEST FILE")
  val request = basicRequest
    .post(uri"http://httpbin.org/post")
    .body(os.read.inputStream(path))
    .response(asString)
  val response = request.send(backend)
  println(response)
}
