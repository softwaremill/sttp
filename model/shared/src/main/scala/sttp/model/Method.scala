package sttp.model

import internal.Validate._
import sttp.model.internal.Validate
import sttp.model.internal.Rfc2616.validateToken

case class Method private (method: String) extends AnyVal {
  override def toString: String = method
}

object Method extends Methods {

  /**
    * @throws IllegalArgumentException If the method value is not a valid token.
    */
  def unsafeApply(method: String): Method = validated(method).getOrThrow
  def validated(method: String): Either[String, Method] =
    Validate.all(validateToken("Method", method))(notValidated(method))
  def notValidated(method: String) = new Method(method)
}

trait Methods {
  val GET: Method = Method.unsafeApply("GET")
  val HEAD: Method = Method.unsafeApply("HEAD")
  val POST: Method = Method.unsafeApply("POST")
  val PUT: Method = Method.unsafeApply("PUT")
  val DELETE: Method = Method.unsafeApply("DELETE")
  val OPTIONS: Method = Method.unsafeApply("OPTIONS")
  val PATCH: Method = Method.unsafeApply("PATCH")
  val CONNECT: Method = Method.unsafeApply("CONNECT")
  val TRACE: Method = Method.unsafeApply("TRACE")
}
