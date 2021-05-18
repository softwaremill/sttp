package sttp.client3.internal

import sttp.client3._
import sttp.model._

import scala.util.Random

class ToRfc2616Converter[R <: RequestT[Identity, _, _]] {

  private val BoundaryChars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray

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
      case MultipartBody(parts)   => handleMultipartBody(parts)
      case FileBody(file, _)      => s"<${file.name}"
      case NoBody                 => ""
    }
  }

  def handleMultipartBody(parts: Seq[Part[RequestBody[_]]]): String = {
    val boundary = generateBoundary()
    parts
      .map { p =>
        p.body match {
          case StringBody(s, _, _) =>
            s"""--$boundary
               |Content-Disposition: form-data; name="${p.name}"
               |
               |$s\n""".stripMargin
          case FileBody(f, _)      =>
            s"""--$boundary
               |Content-Disposition: form-data; name="${p.name}"
               |
               |< ${f.name}\n""".stripMargin
          case _                   => s"--$boundary"
        }
      }
      .mkString("") + s"--$boundary--"
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

  private def generateBoundary(): String = {
    val random = Random
    List
      .fill(32)(BoundaryChars(random.nextInt(BoundaryChars.length)))
      .mkString

  }
}

object ToRfc2616Converter {
  def requestToRfc2616[R <: Request[_, _]]: ToRfc2616Converter[R] = new ToRfc2616Converter[R]
}
