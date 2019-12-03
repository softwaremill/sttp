package sttp.client

import java.io.File

import org.scalatest.Suite
import sttp.client.internal.SttpFile
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait ToCurlConverterTestExtension { suit: Suite with AnyFlatSpec with Matchers =>
  it should "render multipart form data if content is a file" in {
    basicRequest
      .multipartBody(multipartSttpFile("upload", SttpFile.fromPath(new File("myDataSet").toPath)))
      .post(uri"http://localhost")
      .toCurl should include(
      """--form 'upload=@myDataSet'"""
    )
  }
}
