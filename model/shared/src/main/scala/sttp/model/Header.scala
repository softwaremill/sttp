package sttp.model

import scala.util.hashing.MurmurHash3

class Header(val name: String, val value: String) {
  def is(otherName: String): Boolean = name.equalsIgnoreCase(otherName)

  override def toString: String = s"$name: $value"
  override def hashCode(): Int = MurmurHash3.mixLast(name.toLowerCase.hashCode, value.hashCode)
  override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this.eq(h) => true
    case h: Header               => is(h.name) && (value == h.value)
    case _                       => false
  }
}

object Header {
  def apply(name: String, value: String) = new Header(name, value)
  def unapply(h: Header): Option[(String, String)] = Some((h.name, h.value))
}
