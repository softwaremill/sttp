package sttp.client.examples

object PostSerializeJsonMonixAsyncHttpClientCirce extends App {
  import sttp.client._
  import sttp.client.circe._
  import sttp.client.asynchttpclient.monix._
  import io.circe.generic.auto._
  import monix.eval.Task

  case class Info(x: Int, y: String)

  val postTask = AsyncHttpClientMonixBackend().flatMap { implicit backend =>
    val r = basicRequest
      .body(Info(91, "abc"))
      .post(uri"https://httpbin.org/post")

    r.send()
      .flatMap { response => Task(println(s"""Got ${response.code} response, body:\n${response.body}""")) }
      .guarantee(backend.close())
  }

  import monix.execution.Scheduler.Implicits.global
  postTask.runSyncUnsafe()
}
