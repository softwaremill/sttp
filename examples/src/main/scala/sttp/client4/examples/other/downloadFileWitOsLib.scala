// {cat=Other; effects=Direct; backend=HttpClient}: Download file with os-lib support

//> using dep com.lihaoyi::os-lib:0.11.3
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC3

package sttp.client4.examples.other

import sttp.client4.*
import os.*

private val fileSize = 8192
private val dest: os.Path = os.Path(s"/tmp/file-example-$fileSize-bytes")
private val backend: SyncBackend = DefaultSyncBackend()

@main def downloadFileWithOsLib(): Unit = {
  val _ = os.remove(dest)
  val request = basicRequest
    .get(uri"https://httpbin.org/bytes/$fileSize")
    .response(asInputStream(i => os.write(dest, i)))
  val response = request.send(backend)
  println(response.headers)
}
