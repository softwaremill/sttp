package sttp.client3.impl

import sttp.capabilities.WebSockets
import sttp.client3.{Backend, StreamBackend, WebSocketBackend, WebSocketStreamBackend}
import _root_.zio.RIO

package object zio {
  implicit class BackendExtendEnv[R0](delegate: Backend[RIO[R0, *]]) {
    def extendEnv[R1]: Backend[RIO[R0 with R1, *]] =
      new ExtendedEnvBackend[R0, R1, Any](delegate) with Backend[RIO[R0 with R1, *]] {}
  }

  implicit class WebSocketBackendExtendEnv[R0](delegate: WebSocketBackend[RIO[R0, *]]) {
    def extendEnv[R1]: WebSocketBackend[RIO[R0 with R1, *]] =
      new ExtendedEnvBackend[R0, R1, WebSockets](delegate) with WebSocketBackend[RIO[R0 with R1, *]] {}
  }

  implicit class StreamBackendExtendEnv[R0, S](delegate: StreamBackend[RIO[R0, *], S]) {
    def extendEnv[R1]: StreamBackend[RIO[R0 with R1, *], S] =
      new ExtendedEnvBackend[R0, R1, S](delegate) with StreamBackend[RIO[R0 with R1, *], S] {}
  }

  implicit class WebSocketStreamBackendExtendEnv[R0, S](delegate: WebSocketStreamBackend[RIO[R0, *], S]) {
    def extendEnv[R1]: StreamBackend[RIO[R0 with R1, *], S] =
      new ExtendedEnvBackend[R0, R1, S with WebSockets](delegate) with WebSocketStreamBackend[RIO[R0 with R1, *], S] {}
  }
}
