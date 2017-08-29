package com.softwaremill.sttp

import com.softwaremill.sttp.model.BasicRequestBody

/**
  * Use the factory methods `multipart` to conveniently create instances of
  * this class. A part can be then further customised using `fileName`,
  * `contentType` and `header` methods.
  */
case class Multipart(name: String,
                     body: BasicRequestBody,
                     fileName: Option[String] = None,
                     contentType: Option[String] = None,
                     additionalHeaders: Map[String, String] = Map()) {
  def fileName(v: String): Multipart = copy(fileName = Some(v))
  def contentType(v: String): Multipart = copy(contentType = Some(v))
  def header(k: String, v: String): Multipart =
    copy(additionalHeaders = additionalHeaders + (k -> v))
}
