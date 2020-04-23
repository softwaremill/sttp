package sttp.client.internal

private[client] class MessageDigestCompatibility(algorithm: String) {
  require(algorithm == "MD5", s"Unsupported algorithm: $algorithm")

  def digest(input: Array[Byte]): Array[Byte] =
    CryptoMd5.digest(input)
}
