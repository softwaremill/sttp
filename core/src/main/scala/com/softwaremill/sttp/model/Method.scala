package com.softwaremill.sttp.model

case class Method(m: String) extends AnyVal
object Method {
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
