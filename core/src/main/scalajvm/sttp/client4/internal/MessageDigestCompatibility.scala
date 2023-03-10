package sttp.client4.internal

import java.security.MessageDigest

private[client4] class MessageDigestCompatibility(algorithm: String) {
  private lazy val md = MessageDigest.getInstance(algorithm)

  def digest(input: Array[Byte]): Array[Byte] = md.digest(input)
}
