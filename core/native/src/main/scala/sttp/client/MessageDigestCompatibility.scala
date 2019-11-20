package sttp.client

private[client] class MessageDigestCompatibility(algorithm: String) {
  def digest(input: Array[Byte]): Array[Byte] =
    throw new UnsupportedOperationException("MessageDigest is currently not supported for native builds")
}
