// {cat=Hello, World!; effects=Monix; backend=HttpClient}: Post JSON data

//> using scala 2.13
//> using dep com.softwaremill.sttp.client4::monix:4.0.0
//> using dep com.softwaremill.sttp.client4::circe:4.0.0
//> using dep io.circe::circe-generic:0.14.12

package sttp.client4.examples

object PostSerializeJsonMonixHttpClientCirce extends App {
  import sttp.client4._
  import sttp.client4.circe._
  import sttp.client4.httpclient.monix.HttpClientMonixBackend
  import io.circe.generic.auto._
  import monix.eval.Task

  case class Info(x: Int, y: String)

  val postTask = HttpClientMonixBackend().flatMap { backend =>
    val r = basicRequest
      .body(asJson(Info(91, "abc")))
      .post(uri"https://httpbin.org/post")

    r.send(backend)
      .flatMap(response => Task(println(s"""Got ${response.code} response, body:\n${response.body}""")))
      .guarantee(backend.close())
  }

  import monix.execution.Scheduler.Implicits.global
  postTask.runSyncUnsafe()
}
