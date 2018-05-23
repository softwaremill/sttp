package com.softwaremill.sttp

import java.io.File

import com.softwaremill.sttp.file.{File => sttpFile}

trait sttpExtensions {

  def asFile(file: File, overwrite: Boolean = false): ResponseAs[sttpFile, Nothing] =
    ResponseAsFile(sttpFile.fromFile(file), overwrite)

}
