package sttp.client.dom.experimental

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

// remove when https://github.com/scala-js/scala-js-dom/pull/328 is merged and released
// also remove `requestInitDynamic` from AbstractFetchBackend

@js.native
@JSGlobal
class AbortController() extends js.Object {

  val signal: AbortSignal = js.native

  def abort(): Unit = js.native
}

@js.native
trait AbortSignal extends js.Object
