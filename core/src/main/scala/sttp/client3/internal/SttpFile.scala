package sttp.client3.internal

/**
  * A platform agnostic file abstraction.
  *
  * Different platforms have different file representations. Each platform
  * should provide conversions in the `FileCompanionExtensions` trait to convert
  * between their supported representations and the `File` abstraction.
  */
abstract class SttpFile private[internal] (val underlying: Any) extends SttpFileExtensions {
  def name: String
  def size: Long
}

object SttpFile extends SttpFileCompanionExtensions {}
