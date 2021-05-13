package sttp.client3.internal

import sttp.client3._
import sttp.model._
import sttp.client3.NoBody
import sttp.model.MediaType

class ToCurlConverter[R <: RequestT[Identity, _, _]] {
  def apply(request: R): String = {
    val params = List(extractMethod(_), extractUrl(_), extractHeaders(_), extractBody(_), extractOptions(_))
      .map(addSpaceIfNotEmpty)
      .reduce((acc, item) => r => acc(r) + item(r))
      .apply(request)

    s"""curl$params"""
  }

  private def extractMethod(r: R): String = {
    s"--request ${r.method.method}"
  }

  private def extractUrl(r: R): String = {
    s"--url '${r.uri}'"
  }

  private def extractHeaders(r: R): String = {
    r.headers
      // filtering out compression headers so that the results are human-readable, if possible
      .filterNot(_.name.equalsIgnoreCase(HeaderNames.AcceptEncoding))
      .collect { case Header(k, v) =>
        s"""--header '$k: $v'"""
      }
      .mkString(newline)
  }

  private def extractBody(r: R): String = {
    r.body match {
      case StringBody(text, _, _)
          if r.headers
            .map(h => (h.name, h.value))
            .toMap
            .get(HeaderNames.ContentType)
            .forall(_ == MediaType.ApplicationXWwwFormUrlencoded.toString) =>
        s"""--form '${text.replace("'", "\\'")}'"""
      case StringBody(text, _, _) => s"""--data '${text.replace("'", "\\'")}'"""
      case ByteArrayBody(_, _)    => s"--data-binary <PLACEHOLDER>"
      case ByteBufferBody(_, _)   => s"--data-binary <PLACEHOLDER>"
      case InputStreamBody(_, _)  => s"--data-binary <PLACEHOLDER>"
      case StreamBody(_)          => s"--data-binary <PLACEHOLDER>"
      case MultipartBody(parts)   => handleMultipartBody(parts)
      case FileBody(file, _)      => s"""--data-binary @${file.name}"""
      case NoBody                 => ""
    }
  }

  def handleMultipartBody(parts: Seq[Part[RequestBody[_]]]): String = {
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

  private def extractOptions(r: R): String = {
    if (r.options.followRedirects) {
      s"--location${newline}--max-redirs ${r.options.maxRedirects}"
    } else {
      ""
    }
  }

  private def addSpaceIfNotEmpty(fInput: R => String): R => String =
    t => if (fInput(t).isEmpty) "" else s"${newline}${fInput(t)}"

  private def newline: String = " \\\n  "
}

object ToCurlConverter {
  def requestToCurl[R <: Request[_, _]]: ToCurlConverter[R] = new ToCurlConverter[R]
}
