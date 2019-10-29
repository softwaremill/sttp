package sttp.client.okhttp

import sttp.client._

object quick extends SttpApi {
  implicit lazy val backend: SttpBackend[Identity, Nothing, NothingT] = OkHttpSyncBackend()

  /**
    * A starting request which always reads the response body as a string, regardless of the status code.
    */
  val quickRequest: RequestT[Empty, String, Nothing] = basicRequest.response(asStringAlways)
}
