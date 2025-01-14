// {cat=Other; effects=Direct; backend=HttpClient}: Download file

//> using dep com.lihaoyi::os-lib:0.11.3
package sttp.client4.examples

import sttp.client4.*
import os.*

private val path: os.Path =
  os.pwd / "examples" / "src" / "main" / "resources" / "some-text-file.txt"
private val backend: SyncBackend = DefaultSyncBackend()

@main def uploadFileWithOsLib(): Unit = {
  val request = basicRequest
    .post(uri"http://httpbin.org/post")
    .body(os.read.inputStream(path))
    .response(asString)
  val response = request.send(backend)
  println(response)
}
