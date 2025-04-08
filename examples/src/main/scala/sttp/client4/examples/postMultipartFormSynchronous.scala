// {cat=Hello, World!; effects=Direct; backend=HttpClient}: POST multipart form

//> using dep com.softwaremill.sttp.client4::core:4.0.0

package sttp.client4.examples

import sttp.client4.*
import java.nio.file.Files
import java.nio.file.Path
import sttp.model.MediaType

@main def postMultipartFormSynchronous(): Unit =
  withTemporaryFile("Hello, World!".getBytes) { file1 =>
    withTemporaryFile("<img>".getBytes) { file2 =>
      val request = basicRequest
        .multipartBody(
          List(
            multipart("name", "John"),
            multipartFile("bio", file1),
            multipartFile("avatar", file2).contentType(MediaType.ImagePng),
            multipart("link", "http://john.doe.com")
          )
        )
        .post(uri"https://httpbin.org/post")

      val backend: SyncBackend = DefaultSyncBackend()
      val response: Response[Either[String, String]] = request.send(backend)

      // the resposne body should contain a "files" and "form" fields with the uploaded multipart data
      println(response.body)
    }
  }

private def withTemporaryFile[T](data: Array[Byte])(f: Path => T): T = {
  val file = Files.createTempFile("sttp", "demo")
  try
    Files.write(file, data)
    f(file)
  finally
    val _ = Files.deleteIfExists(file)
}
