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

  /** Convenience method to create a [[Response]] instance.
    *
    * In tests using the [[BackendStub]], use [[adjust]] or [[exact]] variants.
    */
  def apply[T](body: T, code: StatusCode): Response[T] =
    Response(body, code, resolveStatusText(code), Nil, Nil, emptyGet)

  /** Convenience method to create a [[Response]] instance.
    *
    * In tests using the [[BackendStub]], use [[adjust]] or [[exact]] variants.
    */
  def apply[T](body: T, code: StatusCode, headers: Seq[Header]): Response[T] =
    Response(body, code, resolveStatusText(code), headers, Nil, emptyGet)

  /** Convenience method to create a [[Response]] instance.
    *
    * In tests using the [[BackendStub]], use [[adjust]] or [[exact]] variants.
    */
  def ok[T](body: T): Response[T] = apply(body, StatusCode.Ok)

  //

  /** Convenience method to create a Response instance, to be used in tests using [[BackendStub]] and partial matchers.
    *
    * Creates a response where the given [[body]] should be adjusted by the backend stub (if possible) as specified in
    * the response description of the request.
    *
    * @see
    *   [[StubBody]] For details on how the body is adjusted.
    */
  def adjust(body: Any): Response[StubBody] = adjust(body, StatusCode.Ok, Nil)

  /** Convenience method to create a Response instance, to be used in tests using [[BackendStub]] and partial matchers.
    *
    * Creates a response where the given [[body]] should be adjusted by the backend stub (if possible) as specified in
    * the response description of the request.
    *
    * @see
    *   [[StubBody]] For details on how the body is adjusted.
    */
  def adjust(body: Any, code: StatusCode): Response[StubBody] = adjust(body, code, Nil)

  /** Convenience method to create a Response instance, to be used in tests using [[BackendStub]] and partial matchers.
    *
    * Creates a response where the given [[body]] should be adjusted by the backend stub (if possible) as specified in
    * the response description of the request.
    *
    * @see
    *   [[StubBody]] For details on how the body is adjusted.
    */
  def adjust(body: Any, code: StatusCode, headers: Seq[Header]): Response[StubBody] =
    apply(StubBody.Adjust(body), code, headers)

  /** Convenience method to create a Response instance, to be used in tests using [[BackendStub]] and partial matchers.
    *
    * Creates a response where the given [[body]] is not adjusted by the backend stub, and returned as-is.
    *
    * @see
    *   [[StubBody]] For details on how the body is handled.
    */
  def exact(body: Any): Response[StubBody] = exact(body, StatusCode.Ok, Nil)

  /** Convenience method to create a Response instance, to be used in tests using [[BackendStub]] and partial matchers.
    *
    * Creates a response where the given [[body]] is not adjusted by the backend stub, and returned as-is.
    *
    * @see
    *   [[StubBody]] For details on how the body is handled.
    */
  def exact(body: Any, code: StatusCode): Response[StubBody] = exact(body, code, Nil)

  /** Convenience method to create a Response instance, to be used in tests using [[BackendStub]] and partial matchers.
    *
    * Creates a response where the given [[body]] is not adjusted by the backend stub, and returned as-is.
    *
    * @see
    *   [[StubBody]] For details on how the body is handled.
    */
  def exact(body: Any, code: StatusCode, headers: Seq[Header]): Response[StubBody] =
    apply(StubBody.Exact(body), code, headers)

  //

  private def resolveStatusText(statusCode: StatusCode): String = StatusText.default(statusCode).getOrElse("")
}
