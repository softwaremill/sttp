package sttp.client4.compression

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.nio.file.Files
import java.io.File
import java.io.FileInputStream

class GZIPCompressingInputStreamTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {
  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0, sizeRange = 10000)

  it should "compress data correctly" in {
    forAll { (input: Array[Byte]) =>
      val compressedStream = new GZIPCompressingInputStream(new ByteArrayInputStream(input))
      val decompressed = new GZIPInputStream(compressedStream).readAllBytes()
      decompressed shouldEqual input
    }
  }

  it should "compress data from a file" in {
    val testFileContent = "test file content"
    withTemporaryFile(testFileContent.getBytes()) { file =>
      val gzipInputStream = new GZIPInputStream(new GZIPCompressingInputStream(new FileInputStream(file)))
      val decompressedBytes = gzipInputStream.readAllBytes()
      decompressedBytes shouldEqual testFileContent.getBytes()
    }
  }

  private def withTemporaryFile[T](content: Array[Byte])(f: File => T): T = {
    val file = Files.createTempFile("sttp", "sttp")
    Files.write(file, content)

    try f(file.toFile)
    finally { val _ = Files.deleteIfExists(file) }
  }
}
