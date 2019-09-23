package sttp.client.testing

object EvalScala {
  import scala.tools.reflect.ToolBox

  def apply(code: String): Any = {
    val m = scala.reflect.runtime.currentMirror
    val tb = m.mkToolBox()
    tb.eval(tb.parse(code))
  }
}
