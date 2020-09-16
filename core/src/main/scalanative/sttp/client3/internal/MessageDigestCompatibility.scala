package sttp.client3.internal

private[client3] class MessageDigestCompatibility(algorithm: String) {
  require(algorithm == "MD5", s"Unsupported algorithm: $algorithm")

  def digest(input: Array[Byte]): Array[Byte] =
    CryptoMd5.digest(input)
}
