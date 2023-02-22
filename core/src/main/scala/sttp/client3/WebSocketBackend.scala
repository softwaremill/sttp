package sttp.client3

import sttp.capabilities.WebSockets

trait WebSocketBackend[F[_]] extends Backend[F] with AbstractBackend[F, WebSockets]
