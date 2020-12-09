package sttp.client3.impl.monix

import monix.reactive.Observable
import monix.reactive.Observable.Transformer
import sttp.client3.sse.ServerSentEvent

object MonixServerSentEvents {
  private val Separator = "\n\n"
  def decodeSSE: Transformer[Array[Byte], ServerSentEvent] = { observable =>
    observable
      .bufferWhileInclusive(array => getSplitAt(array).getOrElse(0) > 0)
      .map(seq => seq.fold(Array.empty)(_ ++ _))
      .map(array => new String(array, "UTF-8"))
      .mapAccumulate[String, List[String]]("") { case (reminder, nextString) =>
        combineFrames(reminder + nextString)
      }
      .flatMap(Observable.fromIterable)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parseEvent)
  }

  //-------
  //this part is utf8 incomplete byte detection from monix from monix.nio.text
  //it is added to this file instead of called from library for compatibility with scalajs
  private def indexIncrement(b: Byte): Int = {
    if ((b & 0x80) == 0) 0 // ASCII byte
    else if ((b & 0xE0) == 0xC0) 2 // first of a 2 byte seq
    else if ((b & 0xF0) == 0xE0) 3 // first of a 3 byte seq
    else if ((b & 0xF8) == 0xF0) 4 // first of a 4 byte seq
    else 0 // following char
  }
  private def getSplitAt(bytes: Array[Byte]): Option[Int] = {
    val lastThree = bytes.drop(0 max bytes.length - 3)
    val addBytesFromLast3 = lastThree.zipWithIndex.foldLeft(Option.empty[Int]) { (acc, elem) =>
      val increment = indexIncrement(elem._1)
      val index = elem._2
      if (index + increment > lastThree.length) {
        Some(index)
      } else {
        acc
      }
    }
    addBytesFromLast3 map (bytes.length + _ - lastThree.length)
  }
  //-------

  private def combineFrames(nextString: String): (String, List[String]) = {
    val splited = nextString.split(Separator).toList
    if (nextString.endsWith(Separator)) {
      ("", splited.filter(_.nonEmpty))
    } else {
      (splited.last, splited.filter(_.nonEmpty).reverse.tail.reverse)
    }
  }
}
