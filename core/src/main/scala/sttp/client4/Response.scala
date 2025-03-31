package sttp.client4

import sttp.model._

import scala.collection.immutable.Seq

/** @param history
  *   If redirects are followed, and there were redirects, contains responses for the intermediate requests. The first
  *   response (oldest) comes first.
  */
case class Response[+T](
    body: T,
    code: StatusCode,
    statusText: String,
    headers: Seq[Header],
    history: List[ResponseMetadata],
    request: RequestMetadata
) extends ResponseMetadata {
  def show(
      includeBody: Boolean = true,
      includeHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): String = {
    val headersStr =
      if (includeHeaders) ", headers: " + Headers.toStringSafe(headers, sensitiveHeaders).mkString(", ") else ""
    val body = if (includeBody) s", body: ${this.body}" else ""
    s"$code $statusText$headersStr$body"
  }

  override def toString: String =
    s"Response($body,$code,$statusText,${Headers.toStringSafe(headers)},$history,$request)"
}

/** For testing, responses can be more conveniently created using [[ResponseStub]]. */
object Response {

  def apply[T](body: T, code: StatusCode, requestMetadata: RequestMetadata): Response[T] =
    Response(body, code, resolveStatusText(code), Nil, Nil, requestMetadata)

  def ok[T](body: T, requestMetadata: RequestMetadata): Response[T] =
    Response(body, StatusCode.Ok, resolveStatusText(StatusCode.Ok), Nil, Nil, requestMetadata)

  private def resolveStatusText(statusCode: StatusCode, provided: String = ""): String =
    if (provided.isEmpty) StatusText.default(statusCode).getOrElse(provided)
    else provided
}
