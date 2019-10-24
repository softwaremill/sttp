package sttp.model

import Part._

/**
  * A decoded representation of a multipart part.
  */
case class Part[T](
    name: String,
    body: T,
    otherDispositionParams: Map[String, String],
    headers: Seq[Header]
) {
  def dispositionParam(k: String, v: String): Part[T] = copy(otherDispositionParams = otherDispositionParams + (k -> v))

  def fileName(v: String): Part[T] = dispositionParam(FileNameDispositionParam, v)
  def fileName: Option[String] = otherDispositionParams.get(FileNameDispositionParam)

  def contentType(v: MediaType): Part[T] = header(Header(HeaderNames.ContentType, v.toString), replaceExisting = true)
  def contentType(v: String): Part[T] = header(Header(HeaderNames.ContentType, v), replaceExisting = true)
  def contentType: Option[String] = header(HeaderNames.ContentType)

  def header(h: Header, replaceExisting: Boolean = false): Part[T] = {
    val current = if (replaceExisting) headers.filterNot(_.is(h.name)) else headers
    this.copy(headers = current :+ h)
  }
  def header(k: String, v: String): Part[T] = header(Header(k, v))
  def header(k: String, v: String, replaceExisting: Boolean): Part[T] = header(Header(k, v), replaceExisting)

  def header(k: String): Option[String] = headers.find(_.name == k).map(_.value)

  def contentDispositionHeaderValue: String = {
    def encode(s: String): String = new String(s.getBytes("utf-8"), "iso-8859-1")
    "form-data; " + dispositionParams.map { case (k, v) => s"$k=${encode(v)}" }.mkString("; ")
  }

  def dispositionParams: Map[String, String] = otherDispositionParams + (NameDispositionParam -> name)
}

object Part {
  def apply[T](
      name: String,
      body: T,
      contentType: Option[MediaType] = None,
      fileName: Option[String] = None,
      otherDispositionParams: Map[String, String] = Map.empty,
      otherHeaders: Seq[Header] = Nil
  ): Part[T] = {
    val p1 = Part(name, body, otherDispositionParams, otherHeaders)
    val p2 = contentType.map(p1.contentType).getOrElse(p1)
    fileName.map(p2.fileName).getOrElse(p2)
  }

  val NameDispositionParam = "name"
  val FileNameDispositionParam = "filename"
}
