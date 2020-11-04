package sttp.client3.examples

//import monix.eval.Task
//import sttp.client3._
//import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend
//import sttp.ws.WebSocket
//
//object WebSocketMonix extends App {
//  import monix.execution.Scheduler.Implicits.global
//
//  def useWebSocket(ws: WebSocket[Task]): Task[Unit] = {
//    def send(i: Int) = ws.sendText(s"Hello $i!")
//    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
//    send(1) *> send(2) *> receive *> receive
//  }
//
//  AsyncHttpClientMonixBackend
//    .resource()
//    .use { backend =>
//      basicRequest
//        .response(asWebSocket(useWebSocket))
//        .get(uri"wss://echo.websocket.org")
//        .send(backend)
//        .void
//    }
//    .runSyncUnsafe()
//}
