package sttp.model

case class Part[T](
    name: String,
    body: T,
    fileName: Option[String] = None,
    contentType: Option[String] = None,
    otherDispositionParams: Map[String, String] = Map.empty,
    additionalHeaders: Seq[Header] = Nil
) {
  def fileName(v: String): Part[T] = copy(fileName = Some(v))
  def contentType(v: String): Part[T] = copy(contentType = Some(v))
  def header(h: Header): Part[T] = copy(additionalHeaders = additionalHeaders :+ h)
  def header(k: String, v: String): Part[T] = header(Header(k, v))

  def contentDispositionHeaderValue: String = {
    def encode(s: String): String = new String(s.getBytes("utf-8"), "iso-8859-1")
    val dispositionParams = List(Some("name" -> name), fileName.map("filename" -> _)).flatten.toMap ++ otherDispositionParams
    "form-data; " + dispositionParams.map { case (k, v) => s"$k=${encode(v)}" }.mkString("; ")
  }
}
