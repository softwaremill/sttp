package sttp.client4.testing

import sttp.client4.{Response, UriContext}
import sttp.model._
import scala.collection.immutable.Seq

object ResponseStub {

  private val emptyGet: RequestMetadata = new RequestMetadata {
    override def method: Method = Method.GET
    override def uri: Uri = uri"http://example.com"
    override def headers: Seq[Header] = Nil
  }

  /** Convenience method to create a Response instance, mainly useful in tests using
   * [[sttp.client4.testing.BackendStub]] and partial matchers.
   */
  def apply[T](body: T, code: StatusCode): Response[T] =
    Response(body, code, resolveStatusText(code), Nil, Nil, emptyGet)

  /** Convenience method to create a Response instance, mainly useful in tests using
   * [[sttp.client4.testing.BackendStub]] and partial matchers.
   */
  def apply[T](body: T, code: StatusCode, statusText: String): Response[T] =
    Response(body, code, resolveStatusText(code, statusText), Nil, Nil, emptyGet)

  /** Convenience method to create a Response instance, mainly useful in tests using
   * [[sttp.client4.testing.BackendStub]] and partial matchers.
   */
  def apply[T](body: T, code: StatusCode, statusText: String, headers: Seq[Header]): Response[T] =
    Response(body, code, resolveStatusText(code, statusText), headers, Nil, emptyGet)

  /** Convenience method to create a Response instance, mainly useful in tests using
   * [[sttp.client4.testing.BackendStub]] and partial matchers.
   */
  def ok[T](body: T): Response[T] = apply(body, StatusCode.Ok)

  private def resolveStatusText(statusCode: StatusCode, provided: String = ""): String =
    if (provided.isEmpty) StatusText.default(statusCode).getOrElse(provided)
    else provided
}
