package com.softwaremill.sttp.file

/**
  * A platform agnostic file abstraction.
  *
  * Different platforms have different file representations. Each platform
  * should provide conversions in the `FileObjectExtensions` trait to convert
  * between their supported representations and the `File` abstraction.
  */
abstract class File private[file] (val underlying: Any) extends FileExtensions {
  def name: String
  def size: Long
}

object File extends FileObjectExtensions {
}
