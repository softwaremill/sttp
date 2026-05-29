package sttp.client4.internal

import sttp.client4._
import sttp.model._

private[client4] object ToCurlConverter {

  def apply(
      request: GenericRequest[_, _],
      omitAcceptEncoding: Boolean = false,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
      sensitiveQueryParams: Set[String] = Set.empty
  ): String = {
    val params = List(
      extractMethod(_),
      extractUrl(sensitiveQueryParams)(_),
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

  private def extractUrl(sensitiveQueryParams: Set[String])(r: GenericRequest[_, _]): String =
    s"--url '${r.uri.toStringSafe(sensitiveQueryParams)}'"

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
        val formValue = p.body match {
          case StringBody(s, _, _) => s"${p.name}=${escapeSingleQuotes(s)}"
          case FileBody(f, _)      => s"${p.name}=@${escapeSingleQuotes(f.name)}"
          case _                   => s"${p.name}=<PLACEHOLDER>"
        }
        s"--form '$formValue${partMetadata(p)}'"
      }
      .mkString(newline)

  private def partMetadata(p: Part[GenericRequestBody[_]]): String = {
    val fileName = p.fileName.fold("")(n => s";filename=${escapeSingleQuotes(n)}")
    val contentType = p.contentType.fold("")(ct => s";type=${escapeSingleQuotes(ct)}")
    // Content-Type is already emitted via ;type= so it is filtered out here to avoid duplication.
    val extraHeaders = p.headers
      .filterNot(_.is(HeaderNames.ContentType))
      .map(h => s""";headers="${escapeSingleQuotes(s"${h.name}: ${h.value}")}"""")
      .mkString
    fileName + contentType + extraHeaders
  }

  private def escapeSingleQuotes(text: String): String = text.replace("'", "\\'")

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
