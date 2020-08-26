package sttp.client

import sttp.model.{Header, StatusCode}

import scala.collection.immutable.Seq

/**
  * @param history If redirects are followed, and there were redirects,
  *                contains responses for the intermediate requests.
  *                The first response (oldest) comes first.
  */
case class Response[T](
    body: T,
    code: StatusCode,
    statusText: String,
    headers: Seq[Header],
    history: List[Response[Unit]]
) extends ResponseMetadata {
  def show(includeBody: Boolean = true): String = {
    val headers = headersToStringSafe.mkString(", ")
    val body = if (includeBody) s", body: ${this.body}" else ""
    s"$code $statusText, headers: $headers$body"
  }

  override def toString: String = s"Response($body,$code,$statusText,$headersToStringSafe,$history)"
}

object Response {

  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[sttp.client.testing.SttpBackendStub]] and partial matchers.
    */
  def apply[T](body: T, code: StatusCode): Response[T] =
    Response(body, code, "", Nil, Nil)

  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[sttp.client.testing.SttpBackendStub]] and partial matchers.
    */
  def apply[T](body: T, code: StatusCode, statusText: String): Response[T] =
    Response(body, code, statusText, Nil, Nil)

  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[sttp.client.testing.SttpBackendStub]] and partial matchers.
    */
  def ok[T](body: T): Response[T] = apply(body, StatusCode.Ok, "OK")
}
