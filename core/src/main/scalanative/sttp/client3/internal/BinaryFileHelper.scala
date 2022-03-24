package sttp.client3.internal

import sttp.client3.{MappedResponseAs, ResponseAs, ResponseAsFile, ResponseAsFromMetadata}

object BinaryFileHelper {
  def getFilePath[T, R](response: ResponseAs[T, R]): Option[SttpFile] = {
    response match {
      case MappedResponseAs(raw, g, _) => getFilePath(raw)
      case rfm: ResponseAsFromMetadata[T, _] =>
        rfm.conditions
          .map(c => getFilePath(c.responseAs))
          .filterNot(_.isEmpty)
          .headOption
          .flatten
      case ResponseAsFile(file) => Some(file)
      case _                    => None
    }
  }
}
