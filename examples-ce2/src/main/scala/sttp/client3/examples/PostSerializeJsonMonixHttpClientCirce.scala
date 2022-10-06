package sttp.client3.examples

object PostSerializeJsonMonixHttpClientCirce extends App {
  import sttp.client3._
  import sttp.client3.circe._
  import sttp.client3.httpclient.monix.HttpClientMonixBackend
  import io.circe.generic.auto._
  import monix.eval.Task

  case class Info(x: Int, y: String)

  val postTask = HttpClientMonixBackend().flatMap { backend =>
    val r = basicRequest
      .body(Info(91, "abc"))
      .post(uri"https://httpbin.org/post")

    r.send(backend)
      .flatMap { response => Task(println(s"""Got ${response.code} response, body:\n${response.body}""")) }
      .guarantee(backend.close())
  }

  import monix.execution.Scheduler.Implicits.global
  postTask.runSyncUnsafe()
}
