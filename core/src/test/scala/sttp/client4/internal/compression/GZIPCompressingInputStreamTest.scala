package sttp.client4.internal.compression

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import sttp.client4.compression.GZIPCompressingInputStream

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
}
