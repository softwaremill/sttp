package sttp.client4.internal.compression

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.util.zip.{CRC32, Deflater}

// based on:
// https://github.com/http4k/http4k/blob/master/core/core/src/main/kotlin/org/http4k/filter/Gzip.kt#L124
// https://stackoverflow.com/questions/11036280/compress-an-inputstream-with-gzip
private[client4] class GZIPCompressingInputStream(
    source: InputStream,
    compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION
) extends InputStream {

  private object State extends Enumeration {
    type State = Value
    val HEADER, DATA, FINALISE, TRAILER, DONE = Value
  }

  import State._

  private val GZIP_MAGIC = 0x8b1f
  private val HEADER_DATA: Array[Byte] = Array(
    GZIP_MAGIC.toByte,
    (GZIP_MAGIC >> 8).toByte,
    Deflater.DEFLATED.toByte,
    0,
    0,
    0,
    0,
    0,
    0,
    0
  )
  private val INITIAL_BUFFER_SIZE = 8192

  private val deflater = new Deflater(Deflater.DEFLATED, true)
  deflater.setLevel(compressionLevel)

  private val crc = new CRC32()
  private var trailer: ByteArrayInputStream = _
  private val header = new ByteArrayInputStream(HEADER_DATA)

  private var deflationBuffer: Array[Byte] = new Array[Byte](INITIAL_BUFFER_SIZE)
  private var stage: State = HEADER

  override def read(): Int = {
    val readBytes = new Array[Byte](1)
    var bytesRead = 0
    while (bytesRead == 0)
      bytesRead = read(readBytes, 0, 1)
    if (bytesRead != -1) readBytes(0) & 0xff else -1
  }

  @throws[IOException]
  override def read(readBuffer: Array[Byte], readOffset: Int, readLength: Int): Int = stage match {
    case HEADER =>
      val bytesRead = header.read(readBuffer, readOffset, readLength)
      if (header.available() == 0) stage = DATA
      bytesRead

    case DATA =>
      if (!deflater.needsInput) {
        deflatePendingInput(readBuffer, readOffset, readLength)
      } else {
        if (deflationBuffer.length < readLength) {
          deflationBuffer = new Array[Byte](readLength)
        }

        val bytesRead = source.read(deflationBuffer, 0, readLength)
        if (bytesRead <= 0) {
          stage = FINALISE
          deflater.finish()
          0
        } else {
          crc.update(deflationBuffer, 0, bytesRead)
          deflater.setInput(deflationBuffer, 0, bytesRead)
          deflatePendingInput(readBuffer, readOffset, readLength)
        }
      }

    case FINALISE =>
      if (deflater.finished()) {
        stage = TRAILER
        val crcValue = crc.getValue.toInt
        val totalIn = deflater.getTotalIn
        trailer = createTrailer(crcValue, totalIn)
        0
      } else {
        deflater.deflate(readBuffer, readOffset, readLength, Deflater.FULL_FLUSH)
      }

    case TRAILER =>
      val bytesRead = trailer.read(readBuffer, readOffset, readLength)
      if (trailer.available() == 0) stage = DONE
      bytesRead

    case DONE => -1

    case _ => throw new IllegalArgumentException(s"Invalid state: $stage")
  }

  private def deflatePendingInput(readBuffer: Array[Byte], readOffset: Int, readLength: Int): Int = {
    var bytesCompressed = 0
    while (!deflater.needsInput && readLength - bytesCompressed > 0)
      bytesCompressed += deflater.deflate(
        readBuffer,
        readOffset + bytesCompressed,
        readLength - bytesCompressed,
        Deflater.FULL_FLUSH
      )
    bytesCompressed
  }

  private def createTrailer(crcValue: Int, totalIn: Int): ByteArrayInputStream =
    new ByteArrayInputStream(
      Array(
        (crcValue >> 0).toByte,
        (crcValue >> 8).toByte,
        (crcValue >> 16).toByte,
        (crcValue >> 24).toByte,
        (totalIn >> 0).toByte,
        (totalIn >> 8).toByte,
        (totalIn >> 16).toByte,
        (totalIn >> 24).toByte
      )
    )

  override def available(): Int = if (stage == DONE) 0 else 1

  @throws[IOException]
  override def close(): Unit = {
    source.close()
    deflater.end()
    if (trailer != null) trailer.close()
    header.close()
  }

  crc.reset()
}
