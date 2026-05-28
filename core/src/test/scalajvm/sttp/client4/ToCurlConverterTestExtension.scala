package sttp.client4

import java.io.File

import org.scalatest.Suite
import sttp.client4.internal.SttpFile
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait ToCurlConverterTestExtension { suit: Suite with AnyFlatSpec with Matchers =>
  it should "render multipart form data if content is a file" in {
    basicRequest
      .multipartBody(multipartSttpFile("upload", SttpFile.fromPath(new File("myDataSet").toPath)))
      .post(uri"http://localhost")
      .toCurl should include(
      """--form 'upload=@myDataSet;filename=myDataSet;type=application/octet-stream'"""
    )
  }

  it should "render multipart file part with overridden filename and content type" in {
    basicRequest
      .multipartBody(
        multipartSttpFile("upload", SttpFile.fromPath(new File("data.bin").toPath))
          .fileName("renamed.pdf")
          .contentType("application/pdf")
      )
      .post(uri"http://localhost")
      .toCurl should include(
      """--form 'upload=@data.bin;filename=renamed.pdf;type=application/pdf'"""
    )
  }
}
