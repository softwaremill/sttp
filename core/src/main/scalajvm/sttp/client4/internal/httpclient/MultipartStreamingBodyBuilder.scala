package sttp.client4.internal.httpclient

import java.util.UUID
import java.nio.charset.StandardCharsets

class MultipartStreamingBodyBuilder {
  private val boundary: String = UUID.randomUUID.toString

  private def headersToString(headers: Map[String, String]): String =
    headers.map { case (k, v) => k + ": " + v }.mkString("\r\n")

  def encodeHeaders(headers: Map[String, String]): Array[Byte] =
    ("--" + boundary + "\r\n" + headersToString(headers) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8)

  val CRLFBytes: Array[Byte] = "\r\n".getBytes(StandardCharsets.UTF_8)

  def getBoundary: String = boundary

  def encodeString(value: String, headers: Map[String, String]): Array[Byte] =
    encodeHeaders(headers) ++ value.getBytes(StandardCharsets.UTF_8) ++ CRLFBytes

  def encodeBytes(value: Array[Byte], headers: Map[String, String]): Array[Byte] = {
    encodeHeaders(headers) ++ value ++ CRLFBytes
  }

  def lastBoundary: Array[Byte] = {
    val lastPart = "--" + boundary + "--"
    lastPart.getBytes(StandardCharsets.UTF_8)
  }
}
