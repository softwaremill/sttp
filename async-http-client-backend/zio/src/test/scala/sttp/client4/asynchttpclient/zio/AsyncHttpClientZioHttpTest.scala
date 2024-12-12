package sttp.client4.asynchttpclient.zio

import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client4.impl.zio.ZioTestBase
import sttp.client4.testing.ConvertToFuture
import zio.Task
import zio.ZIO

class AsyncHttpClientZioHttpTest extends AsyncHttpClientHttpTest[Task] with ZioTestBase {

  override val backend: Backend[Task] =
    unsafeRunSyncOrThrow(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  "throw an exception instead of ZIO defect if the header value is invalid" in {

    val r = basicRequest
      .get(uri"https://example.com")
      .header("X-Api-Key", " Я ЛЮБЛЮ БОРЩ")
      .response(asString)
      .send(backend)

    val resultTask: Task[Any] = r.catchSomeCause {
      case c if c.defects.nonEmpty => ZIO.fail(new Exception("Defect occurred during the operation"))
      case _                       => ZIO.succeed("No defects occurred during the operation")
    }

    convertToFuture.toFuture(resultTask).map(_ => succeed)
  }
}
