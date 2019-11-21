package sttp.client

import java.security.MessageDigest

private[client] class MessageDigestCompatibility(algorithm: String) {
  private lazy val md = MessageDigest.getInstance(algorithm)

  def digest(input: Array[Byte]): Array[Byte] = md.digest(input)
}
