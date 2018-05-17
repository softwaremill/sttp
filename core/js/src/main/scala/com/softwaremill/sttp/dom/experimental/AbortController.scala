package com.softwaremill.sttp.dom.experimental

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

// https://developer.mozilla.org/en-US/docs/Web/API/AbortController

@js.native
@JSGlobal
class AbortController() extends js.Object {

  val signal: AbortSignal = js.native

  def abort(): Unit = js.native
}

@js.native
trait AbortSignal extends js.Object
