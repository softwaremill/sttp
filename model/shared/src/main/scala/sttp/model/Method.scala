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
  def unsafeApply(method: String): Method = safeApply(method).getOrThrow
  def safeApply(method: String): Either[String, Method] =
    Validate.all(validateToken("Method", method))(notValidated(method))
  def notValidated(method: String) = new Method(method)
}

trait Methods {
  val GET: Method = Method.notValidated("GET")
  val HEAD: Method = Method.notValidated("HEAD")
  val POST: Method = Method.notValidated("POST")
  val PUT: Method = Method.notValidated("PUT")
  val DELETE: Method = Method.notValidated("DELETE")
  val OPTIONS: Method = Method.notValidated("OPTIONS")
  val PATCH: Method = Method.notValidated("PATCH")
  val CONNECT: Method = Method.notValidated("CONNECT")
  val TRACE: Method = Method.notValidated("TRACE")
}
