// {cat=Other; effects=Direct; backend=HttpClient}: Download file

//> using dep com.lihaoyi::os-lib:0.11.3
package sttp.client4.examples

import sttp.client4.*
import os.*

private val fileSize = 8192
private val dest: os.Path =
  os.pwd / "examples" / "src" / "main" / "resources" / s"file-example-$fileSize-bytes"
private val backend: SyncBackend = DefaultSyncBackend()

@main def downloadFileWithOsLib(): Unit = {
  os.remove(dest)
  val request = basicRequest
    .get(uri"https://httpbin.org/bytes/$fileSize")
    .response(asInputStream(i => os.write(dest, i)))
  val response = request.send(backend)
  println(response.headers)
}
