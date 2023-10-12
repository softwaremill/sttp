package sttp.client4

package object upicklejson {
  object default extends SttpUpickleApi {
    override val upickleApi: upickle.default.type = upickle.default
  }
}
