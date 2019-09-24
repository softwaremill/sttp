package sttp.client

import sttp.client.model._
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
) extends ResponseExtensions[T]
    with ResponseMetadata {

  override def toString: String = s"Response($body,$code,$statusText,$headers,$history)"
}

object Response {

  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[com.softwaremill.sttp.testing.SttpBackendStub]] and partial matchers.
    */
  def apply[T](body: T, code: StatusCode, statusText: String): Response[T] =
    Response(body, code, statusText, Nil, Nil)

  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[com.softwaremill.sttp.testing.SttpBackendStub]] and partial matchers.
    */
  def ok[T](body: T): Response[T] = apply(body, StatusCode.Ok, "OK")
}
