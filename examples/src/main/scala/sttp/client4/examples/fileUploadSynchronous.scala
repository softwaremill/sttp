// {cat=Hello, World!; effects=Direct; backend=HttpClient}: Upload file

//> using dep com.softwaremill.sttp.client4::core:4.0.0-M24

package sttp.client4.examples

import sttp.client4.*

import java.nio.file.Files
import java.nio.file.Path

@main def fileUploadSynchronous(): Unit =
  withTemporaryFile("Hello, World!".getBytes) { file =>
    val request = basicRequest
      .body(file)
      .post(uri"https://httpbin.org/post")

    val backend: SyncBackend = DefaultSyncBackend()
    val response: Response[Either[String, String]] = request.send(backend)

    // the uploaded data should be echoed in the "data" field of the response body
    println(response.body)
  }

private def withTemporaryFile[T](data: Array[Byte])(f: Path => T): T = {
  val file = Files.createTempFile("sttp", "demo")
  try
    Files.write(file, data)
    f(file)
  finally
    val _ = Files.deleteIfExists(file)
}
