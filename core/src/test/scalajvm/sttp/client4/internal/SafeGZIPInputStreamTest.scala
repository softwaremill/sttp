package sttp.client4.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, EOFException}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

class SafeGZIPInputStreamTest extends AnyFlatSpec with Matchers {

  "A safe GZIP stream" should "handle empty stream without throwing EOFException" in {
    val emptyStream = new ByteArrayInputStream(Array.empty[Byte])

    val _ = assertThrows[EOFException] { // a working problem reproducer for a regular GZIPInputStream failure
      new java.util.zip.GZIPInputStream(emptyStream)
    }

    val safeStream = SafeGZIPInputStream.apply(emptyStream)
    val result = safeStream.read()

    result shouldBe -1
  }

  it should "propagate EOFException during read operations" in {
    val content = "Some content"
    val baos = new ByteArrayOutputStream()
    val incompleteOutStream = new GZIPOutputStream(baos)
    incompleteOutStream.write(content.getBytes("UTF-8"))
    incompleteOutStream.flush() // don't close properly to simulate failure scenario

    val invalidGzipData = baos.toByteArray
    val corruptedStream = new ByteArrayInputStream(invalidGzipData)
    val safeStream = SafeGZIPInputStream.apply(corruptedStream)

    val error = intercept[EOFException] {
      safeStream.read()
    }
    error.getMessage shouldBe "Unexpected end of ZLIB input stream"
  }

  it should "handle non-empty gzipped content correctly" in {
    val testMessage = "Hello, world!"
    val data = createGzippedContent(testMessage)
    val gzippedContent = new ByteArrayInputStream(data)
    val safeStream = SafeGZIPInputStream.apply(gzippedContent)

    // Read the entire content
    val buffer = new Array[Byte](1024)
    val bytesRead = safeStream.read(buffer)

    val decompressedContent = new String(buffer, 0, bytesRead, "UTF-8")
    decompressedContent shouldBe testMessage
  }

  private def createGzippedContent(content: String): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val gzipOut = new GZIPOutputStream(baos)
    gzipOut.write(content.getBytes("UTF-8"))
    gzipOut.close()
    baos.toByteArray
  }

  it should "let callers handle other failures" in {
    val invalidData = Array[Byte](1, 2, 3, 4)
    val invalidStream = new ByteArrayInputStream(invalidData)
    val safeStream = SafeGZIPInputStream.apply(invalidStream)

    val error = intercept[java.util.zip.ZipException] {
      safeStream.read()
    }
    error.getMessage shouldBe "Not in GZIP format"
  }

  it should "require callers to handle stream lifecycle states" in {
    val content = "Test content"
    val data = createGzippedContent(content)
    val gzippedContent = new ByteArrayInputStream(data)
    val safeStream = SafeGZIPInputStream.apply(gzippedContent)

    safeStream.close()

    val error = intercept[java.io.IOException] {
      safeStream.read()
    }
    error.getMessage shouldBe "Stream closed"
  }

}
