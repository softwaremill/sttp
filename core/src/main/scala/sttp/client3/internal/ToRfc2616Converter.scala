package sttp.client3.internal

import sttp.client3._
import sttp.model._

class ToRfc2616Converter[R <: RequestT[Identity, _, _]] {
  def apply(request: R): String = {
    val extractMethod = request.method.method
    val extractUri = request.uri
    val result = s"$extractMethod $extractUri"
    val headers = extractHeaders(request)
    val resultWithHeaders = if (headers.isEmpty) result else result + s"\n$headers"
    val body = extractBody(request)
    if (body.isEmpty) resultWithHeaders else resultWithHeaders + s"\n\n$body"
  }

  private def extractBody(r: R): String = {
    r.body match {
      case StringBody(text, _, _) => s"$text"
      case ByteArrayBody(_, _)    => "<PLACEHOLDER>"
      case ByteBufferBody(_, _)   => "<PLACEHOLDER>"
      case InputStreamBody(_, _)  => "<PLACEHOLDER>"
      case StreamBody(_)          => "<PLACEHOLDER>"
      case MultipartBody(parts)   => handleMultipartBody(parts) + "--<PLACEHOLDER>--"
      case FileBody(file, _)      => s"<${file.name}"
      case NoBody                 => ""
    }
  }

  def handleMultipartBody(parts: Seq[Part[RequestBody[_]]]): String = {
    parts
      .map { p =>
        p.body match {
          case StringBody(s, _, _) =>
            s"""--<PLACEHOLDER>
               |Content-Disposition: form-data; name="${p.name}"
               |
               |$s\n""".stripMargin
          case FileBody(f, _)      =>
            s"""--<PLACEHOLDER>
               |Content-Disposition: form-data; name="${p.name}"
               |
               |< ${f.name}\n""".stripMargin
          case _                   => s"-<PLACEHOLDER>"
        }
      }
      .mkString("")
  }

  private def extractHeaders(r: R): String = {
    r.headers
      // filtering out compression headers so that the results are human-readable, if possible
      .filterNot(_.name.equalsIgnoreCase(HeaderNames.AcceptEncoding))
      .collect { case Header(k, v) =>
        s"""$k: $v"""
      }
      .mkString("\n")
  }
}

object ToRfc2616Converter {
  def requestToRfc2616[R <: Request[_, _]]: ToRfc2616Converter[R] = new ToRfc2616Converter[R]
}
