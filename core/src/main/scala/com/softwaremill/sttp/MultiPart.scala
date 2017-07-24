package com.softwaremill.sttp

import com.softwaremill.sttp.model.BasicRequestBody

/**
  * Use the factory methods `multiPart` to conveniently create instances of
  * this class. A part can be then further customised using `fileName`,
  * `contentType` and `header` methods.
  */
case class MultiPart(name: String,
                     data: BasicRequestBody,
                     fileName: Option[String] = None,
                     contentType: Option[String] = None,
                     additionalHeaders: Map[String, String] = Map()) {
  def fileName(v: String): MultiPart = copy(fileName = Some(v))
  def contentType(v: String): MultiPart = copy(contentType = Some(v))
  def header(k: String, v: String): MultiPart =
    copy(additionalHeaders = additionalHeaders + (k -> v))
}
