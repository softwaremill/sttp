// {cat=WebSocket; effects=Direct; backend=HttpClient}: Connect to & interact with a WebSocket, using Ox channels for streaming

//> using dep com.softwaremill.sttp.client4::ox:4.0.8

// package sttp.client4.examples.ws

// import ox.*
// import ox.flow.Flow
// import sttp.client4.*
// import sttp.client4.impl.ox.ws.*
// import sttp.client4.ws.SyncWebSocket
// import sttp.client4.ws.sync.*
// import sttp.ws.WebSocketFrame

// import scala.concurrent.duration.*

// @main def wsOxExample: Unit =
//   def useWebSocket(ws: SyncWebSocket): Unit =
//     runWebSocketPipe(ws): incoming =>
//       incoming
//         .tap(frame => println(s"RECEIVED: $frame"))
//         .drain()
//         .merge(
//           // Waiting for 1 second before completing the outgoing flow, so that the responses are received.
//           // Since propagateDoneRight = true, the web socket will be terminated after the outgoing flow is completed.
//           Flow.fromValues(1, 2, 3).map(i => WebSocketFrame.text(s"Frame no $i")) ++ Flow.timeout(1.second),
//           propagateDoneRight = true
//         )

//   val backend = DefaultSyncBackend()
//   try
//     basicRequest
//       .get(uri"wss://ws.postman-echo.com/raw")
//       .response(asWebSocket(useWebSocket))
//       .send(backend)
//       .discard
//   finally
//     backend.close()
