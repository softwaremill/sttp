package sttp.client4.internal

private[client4] class MessageDigestCompatibility(algorithm: String) {
  require(algorithm == "MD5", s"Unsupported algorithm: $algorithm")

  def digest(input: Array[Byte]): Array[Byte] =
    CryptoMd5.digest(input)
}
