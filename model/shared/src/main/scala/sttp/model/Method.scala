package sttp.model

case class Method(method: String) extends AnyVal

object Method extends Methods

trait Methods {
  val GET = Method("GET")
  val HEAD = Method("HEAD")
  val POST = Method("POST")
  val PUT = Method("PUT")
  val DELETE = Method("DELETE")
  val OPTIONS = Method("OPTIONS")
  val PATCH = Method("PATCH")
  val CONNECT = Method("CONNECT")
  val TRACE = Method("TRACE")
}
