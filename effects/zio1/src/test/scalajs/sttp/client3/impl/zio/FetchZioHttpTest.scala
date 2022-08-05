package sttp.client3.impl.zio

import zio.Task
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.testing.{AbstractFetchHttpTest, ConvertToFuture}

class FetchZioHttpTest extends AbstractFetchHttpTest[Task, ZioStreams] with ZioRuntimeUtils {

  override val backend: SttpBackend[Task, ZioStreams] = FetchZioBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override protected def supportsCustomMultipartContentType = false

  override protected def supportsCustomMultipartEncoding = false
}
