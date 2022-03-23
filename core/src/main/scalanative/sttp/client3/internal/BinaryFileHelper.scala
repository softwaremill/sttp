package sttp.client3.internal

import sttp.client3.{MappedResponseAs, ResponseAs, ResponseAsFile, ResponseAsFromMetadata}

object BinaryFileHelper {
  def getFilePath[T, R](response: ResponseAs[T, R], isBinary: Boolean): Option[SttpFile] = {
    if (isBinary) {
      response match {
        case MappedResponseAs(raw, g, _) => getFilePath(raw, isBinary)
        case rfm: ResponseAsFromMetadata[T, _] =>
          rfm.conditions
            .map(c => getFilePath(c.responseAs, isBinary))
            .filterNot(_.isEmpty)
            .head
        case ResponseAsFile(file) => Some(file)
        case _                    => None
      }
    } else {
      None
    }
  }
}
