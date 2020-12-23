package sttp.client3.internal

import sttp.client3._
import sttp.model._
import sttp.client3.NoBody
import sttp.model.MediaType

class ToCurlConverter {
  def apply(request: Request[_, _]): String = {
    val params = List(extractOptions(_), extractMethod(_), extractHeaders(_), extractBody(_))
      .map(addSpaceIfNotEmpty)
      .reduce((acc, item) => r => acc(r) + item(r))
      .apply(request)

    s"""curl$params '${request.uri}'"""
  }

  private def extractMethod(r: Request[_, _]): String = s"-X ${r.method.method}"

  private def extractHeaders(r: Request[_, _]): String = {
    r.headers
      // filtering out compression headers so that the results are human-readable, if possible
      .filterNot(_.name.equalsIgnoreCase(HeaderNames.AcceptEncoding))
      .collect { case Header(k, v) =>
        s"""-H '$k: $v'"""
      }
      .mkString(" ")
  }

  private def extractBody(r: Request[_, _]): String = {
    r.body match {
      case StringBody(text, _, _)
          if r.headers
            .map(h => (h.name, h.value))
            .toMap
            .get(HeaderNames.ContentType)
            .forall(_ == MediaType.ApplicationXWwwFormUrlencoded.toString) =>
        s"""-F '${text.replace("'", "\\'")}'"""
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
      .mkString(" ")
  }

  private def extractOptions(r: Request[_, _]): String = {
    if (r.options.followRedirects) {
      s"-L --max-redirs ${r.options.maxRedirects}"
    } else {
      ""
    }
  }

  private def addSpaceIfNotEmpty(fInput: Request[_, _] => String): Request[_, _] => String =
    t => if (fInput(t).isEmpty) "" else s" ${fInput(t)}"
}

object ToCurlConverter {
  def requestToCurl: ToCurlConverter = new ToCurlConverter
}
