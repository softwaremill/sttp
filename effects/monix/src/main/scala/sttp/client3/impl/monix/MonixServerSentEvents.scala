package sttp.client3.impl.monix

import monix.reactive.Observable
import monix.reactive.Observable.Transformer
import sttp.model.sse.ServerSentEvent

object MonixServerSentEvents {
  private val Separator = "\n\n"
  val parse: Transformer[Array[Byte], ServerSentEvent] = { observable =>
    observable
      .bufferWhileInclusive(array => missingBytes(array).getOrElse(0) > 0)
      .map(seq => seq.fold(Array.empty[Byte])(_ ++ _))
      .map(array => new String(array, "UTF-8"))
      .mapAccumulate[String, List[String]]("") { case (reminder, nextString) =>
        combineFrames(reminder + nextString)
      }
      .flatMap(Observable.fromIterable)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parse)
  }

  //-------
  //this part is utf8 incomplete byte detection from monix from monix.nio.text.UTF8Codec
  //it is added to this file instead of called from library for compatibility with scalajs that does not have nio
  private def indexIncrement(b: Byte): Int = {
    if ((b & 0x80) == 0) 0 // ASCII byte
    else if ((b & 0xe0) == 0xc0) 2 // first of a 2 byte seq
    else if ((b & 0xf0) == 0xe0) 3 // first of a 3 byte seq
    else if ((b & 0xf8) == 0xf0) 4 // first of a 4 byte seq
    else 0 // following char
  }
  private def missingBytes(bytes: Array[Byte]): Option[Int] = {
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
