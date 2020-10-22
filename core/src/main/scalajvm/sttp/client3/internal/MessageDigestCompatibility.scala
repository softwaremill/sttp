package sttp.client3.internal

import java.security.MessageDigest

private[client3] class MessageDigestCompatibility(algorithm: String) {
  private lazy val md = MessageDigest.getInstance(algorithm)

  def digest(input: Array[Byte]): Array[Byte] = md.digest(input)
}
