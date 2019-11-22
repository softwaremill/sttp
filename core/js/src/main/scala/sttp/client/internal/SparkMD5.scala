package sttp.client.internal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal
private[client] object SparkMD5 extends js.Object {
  @js.native
  object ArrayBuffer extends js.Object {
    def hash(arr: scala.scalajs.js.typedarray.ArrayBuffer, raw: Boolean = false): String = js.native
  }
}
