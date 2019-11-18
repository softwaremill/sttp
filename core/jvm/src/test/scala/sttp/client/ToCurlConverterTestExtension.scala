package sttp.client

import java.io.File

import org.scalatest.{FlatSpec, Matchers, Suite}
import sttp.client.internal.SttpFile

trait ToCurlConverterTestExtension { suit: Suite with FlatSpec with Matchers =>
  it should "render multipart form data if content is a file" in {
    basicRequest
      .multipartBody(multipartSttpFile("upload", SttpFile.fromPath(new File("myDataSet").toPath)))
      .post(uri"http://localhost")
      .toCurl should include(
      """--form 'upload=@myDataSet'"""
    )
  }
}
