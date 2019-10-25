package sttp.model

import sttp.model.internal.{Rfc2616, Validate}
import sttp.model.internal.Validate._

import scala.util.hashing.MurmurHash3

/**
  * An HTTP header. The `name` property is case-insensitive during equality checks.
  *
  * The `name` and `value` should be already encoded (if necessary), as when serialised, they end up unmodified in
  * the header.
  */
class Header(val name: String, val value: String) {

  /**
    * Check if the name of this header is the same as the given one. The names are compared in a case-insensitive way.
    */
  def is(otherName: String): Boolean = name.equalsIgnoreCase(otherName)

  /**
    * @return Representation in the format: `[name]: [value]`.
    */
  override def toString: String = s"$name: $value"
  override def hashCode(): Int = MurmurHash3.mixLast(name.toLowerCase.hashCode, value.hashCode)
  override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this.eq(h) => true
    case h: Header               => is(h.name) && (value == h.value)
    case _                       => false
  }
}

object Header {
  def unapply(h: Header): Option[(String, String)] = Some((h.name, h.value))

  private[model] def validateName(name: String): Option[String] = {
    if (Rfc2616.Token.unapplySeq(name).isEmpty) {
      Some("Header name can not contain separators: ()<>@,;:\\\"/[]?={}, or whitespace.")
    } else None
  }

  /**
    * @throws IllegalArgumentException If the header name contains illegal characters.
    */
  def unsafeApply(name: String, value: String): Header = validated(name, value).getOrThrow

  def validated(name: String, value: String): Either[String, Header] = {
    Validate.all(validateName(name))(new Header(name, value))
  }

  def notValidated(name: String, value: String): Header = new Header(name, value)
}
