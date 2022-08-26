package sttp.client3.httpclient

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend

package object zio {
  type SttpClient = SttpBackend[_root_.zio.Task, ZioStreams with WebSockets]
}
