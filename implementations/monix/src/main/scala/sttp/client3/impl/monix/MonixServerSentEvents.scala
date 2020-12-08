package sttp.client3.impl.monix

import monix.nio.text.UTF8Codec
import monix.reactive.Observable
import monix.reactive.Observable.Transformer
import sttp.client3.sse.ServerSentEvent

object MonixServerSentEvents {
  private val Separator = "\n\n"
  def decodeSSE: Transformer[Array[Byte], ServerSentEvent] = { observable =>
    observable
      .pipeThrough(UTF8Codec.utf8Decode)
      .mapAccumulate[String, List[String]]("") { case (reminder, nextString) =>
        combineFrames(reminder + nextString)
      }
      .flatMap(Observable.fromIterable)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parseEvent)
  }

  private def combineFrames(nextString: String): (String, List[String]) = {
    val splited = nextString.split(Separator).toList
    if (nextString.endsWith(Separator)) {
      ("", splited.filter(_.nonEmpty))
    } else {
      (splited.last, splited.filter(_.nonEmpty).reverse.tail.reverse)
    }
  }
}
