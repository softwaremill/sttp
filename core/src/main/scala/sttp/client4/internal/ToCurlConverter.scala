package sttp.client4.internal

import sttp.client4._
import sttp.model._

private[client4] object ToCurlConverter {

  def apply(request: GenericRequest[_, _]): String =
    apply(request, HeaderNames.SensitiveHeaders, omitAcceptEncoding = false)

  def apply(request: GenericRequest[_, _], omitAcceptEncoding: Boolean): String =
    apply(request, HeaderNames.SensitiveHeaders, omitAcceptEncoding = omitAcceptEncoding)

  def apply(request: GenericRequest[_, _], sensitiveHeaders: Set[String]): String =
    apply(request, sensitiveHeaders, omitAcceptEncoding = false)

  def apply(request: GenericRequest[_, _], sensitiveHeaders: Set[String], omitAcceptEncoding: Boolean): String = {
    val params = List(
      extractMethod(_),
      extractUrl(_),
      extractHeaders(sensitiveHeaders, omitAcceptEncoding)(_),
      extractBody(_),
      extractOptions(_)
    )
      .map(addSpaceIfNotEmpty)
      .reduce((acc, item) => (r: GenericRequest[_, _]) => acc(r) + item(r))
      .apply(request)

    s"""curl$params"""
  }

  private def extractMethod(r: GenericRequest[_, _]): String =
    s"--request ${r.method.method}"

  private def extractUrl(r: GenericRequest[_, _]): String =
    s"--url '${r.uri}'"

  private def extractHeaders(sensitiveHeaders: Set[String], omitAcceptEncoding: Boolean)(
      r: GenericRequest[_, _]
  ): String =
    (if (!omitAcceptEncoding) {
       r.headers
     } else {
       // filtering out compression headers so that the results are human-readable, if possible
       r.headers.filterNot(_.name.equalsIgnoreCase(HeaderNames.AcceptEncoding))
     })
      .map(h => s"--header '${h.toStringSafe(sensitiveHeaders)}'")
      .mkString(newline)

  private def extractBody(r: GenericRequest[_, _]): String =
    r.body match {
      case StringBody(text, _, _) => s"""--data-raw '${text.replace("'", "\\'")}'"""
      case ByteArrayBody(_, _)    => s"--data-binary <PLACEHOLDER>"
      case ByteBufferBody(_, _)   => s"--data-binary <PLACEHOLDER>"
      case InputStreamBody(_, _)  => s"--data-binary <PLACEHOLDER>"
      case StreamBody(_)          => s"--data-binary <PLACEHOLDER>"
      case m: MultipartBody[_]    => handleMultipartBody(m.parts)
      case FileBody(file, _)      => s"""--data-binary @${file.name}"""
      case NoBody                 => ""
    }

  def handleMultipartBody(parts: Seq[Part[GenericRequestBody[_]]]): String =
    parts
      .map { p =>
        p.body match {
          case StringBody(s, _, _) => s"--form '${p.name}=$s'"
          case FileBody(f, _)      => s"--form '${p.name}=@${f.name}'"
          case _                   => s"--data-binary <PLACEHOLDER>"
        }
      }
      .mkString(newline)

  private def extractOptions(r: GenericRequest[_, _]): String =
    if (r.options.followRedirects) {
      s"--location${newline}--max-redirs ${r.options.maxRedirects}"
    } else {
      ""
    }

  private def addSpaceIfNotEmpty(fInput: GenericRequest[_, _] => String): GenericRequest[_, _] => String =
    t => if (fInput(t).isEmpty) "" else s"${newline}${fInput(t)}"

  private def newline: String = " \\\n  "
}
