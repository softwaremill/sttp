package sttp.client3.internal

import sttp.client3._
import sttp.model._

object ToCurlConverter {

  def apply(request: AbstractRequest[_, _]): String = apply(request, HeaderNames.SensitiveHeaders)

  def apply(request: AbstractRequest[_, _], sensitiveHeaders: Set[String]): String = {
    val params = List(
      extractMethod(_),
      extractUrl(_),
      (r: AbstractRequest[_, _]) => extractHeaders(r, sensitiveHeaders),
      extractBody(_),
      extractOptions(_)
    )
      .map(addSpaceIfNotEmpty)
      .reduce((acc, item) => (r: AbstractRequest[_, _]) => acc(r) + item(r))
      .apply(request)

    s"""curl$params"""
  }

  private def extractMethod(r: AbstractRequest[_, _]): String = {
    s"--request ${r.method.method}"
  }

  private def extractUrl(r: AbstractRequest[_, _]): String = {
    s"--url '${r.uri}'"
  }

  private def extractHeaders(r: AbstractRequest[_, _], sensitiveHeaders: Set[String]): String = {
    r.headers
      // filtering out compression headers so that the results are human-readable, if possible
      .filterNot(_.name.equalsIgnoreCase(HeaderNames.AcceptEncoding))
      .map(h => s"--header '${h.toStringSafe(sensitiveHeaders)}'")
      .mkString(newline)
  }

  private def extractBody(r: AbstractRequest[_, _]): String = {
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
  }

  def handleMultipartBody(parts: Seq[Part[AbstractBody[_]]]): String = {
    parts
      .map { p =>
        p.body match {
          case StringBody(s, _, _) => s"--form '${p.name}=$s'"
          case FileBody(f, _)      => s"--form '${p.name}=@${f.name}'"
          case _                   => s"--data-binary <PLACEHOLDER>"
        }
      }
      .mkString(newline)
  }

  private def extractOptions(r: AbstractRequest[_, _]): String = {
    if (r.options.followRedirects) {
      s"--location${newline}--max-redirs ${r.options.maxRedirects}"
    } else {
      ""
    }
  }

  private def addSpaceIfNotEmpty(fInput: AbstractRequest[_, _] => String): AbstractRequest[_, _] => String =
    t => if (fInput(t).isEmpty) "" else s"${newline}${fInput(t)}"

  private def newline: String = " \\\n  "
}
