package com.softwaremill.sttp

import com.softwaremill.sttp.dom.experimental.{File => DomFile}
import com.softwaremill.sttp.file.{File => sttpFile}

trait sttpExtensions {

  def asFile(file: DomFile, overwrite: Boolean = false): ResponseAs[sttpFile, Nothing] =
    ResponseAsFile(sttpFile.fromDomFile(file), overwrite)

}
