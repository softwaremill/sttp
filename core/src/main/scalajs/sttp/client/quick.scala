package sttp.client

import scala.concurrent.Future

object quick extends SttpApi {
  lazy val backend: SttpBackend[Future, Any] = FetchBackend()
}
