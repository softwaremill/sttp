package sttp.client4.internal

import sttp.client4._
import sttp.model._

import scala.util.Random

private[client4] object ToRfc2616Converter {

  def requestToRfc2616(request: GenericRequest[_, _]): String = apply(request, HeaderNames.SensitiveHeaders)

  def requestToRfc2616(request: GenericRequest[_, _], sensitiveHeaders: Set[String]): String =
    apply(request, sensitiveHeaders)

  private val BoundaryChars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray

  def apply(request: GenericRequest[_, _]): String = apply(request, HeaderNames.SensitiveHeaders)

  def apply(request: GenericRequest[_, _], sensitiveHeaders: Set[String]): String = {
    val extractMethod = request.method.method
    val extractUri = request.uri
    val result = s"$extractMethod $extractUri"
    val headers = extractHeaders(request, sensitiveHeaders)
    val resultWithHeaders = if (headers.isEmpty) result else result + s"\n$headers"
    val body = extractBody(request)
    if (body.isEmpty) resultWithHeaders else resultWithHeaders + s"\n\n$body"
  }

  private def extractBody(r: GenericRequest[_, _]): String =
    r.body match {
      case StringBody(text, _, _) => s"$text"
      case ByteArrayBody(_, _)    => "<PLACEHOLDER>"
      case ByteBufferBody(_, _)   => "<PLACEHOLDER>"
      case InputStreamBody(_, _)  => "<PLACEHOLDER>"
      case StreamBody(_)          => "<PLACEHOLDER>"
      case m: MultipartBody[_]    => handleMultipartBody(m.parts)
      case FileBody(file, _)      => s"<${file.name}"
      case NoBody                 => ""
    }

  def handleMultipartBody(parts: Seq[Part[GenericRequestBody[_]]]): String = {
    val boundary = generateBoundary()
    parts
      .map { p =>
        p.body match {
          case StringBody(s, _, _) =>
            s"""--$boundary
               |Content-Disposition: form-data; name="${p.name}"
               |
               |$s\n""".stripMargin
          case FileBody(f, _) =>
            s"""--$boundary
               |Content-Disposition: form-data; name="${p.name}"
               |
               |< ${f.name}\n""".stripMargin
          case _ => s"--$boundary"
        }
      }
      .mkString("") + s"--$boundary--"
  }

  private def extractHeaders(r: GenericRequest[_, _], sensitiveHeaders: Set[String]): String =
    r.headers
      // filtering out compression headers so that the results are human-readable, if possible
      .filterNot(_.name.equalsIgnoreCase(HeaderNames.AcceptEncoding))
      .map(h => h.toStringSafe(sensitiveHeaders))
      .mkString("\n")

  private def generateBoundary(): String = {
    val random = Random
    List
      .fill(32)(BoundaryChars(random.nextInt(BoundaryChars.length)))
      .mkString

  }
}
