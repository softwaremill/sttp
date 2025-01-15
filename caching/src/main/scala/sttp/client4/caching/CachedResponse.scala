package sttp.client4.caching

import sttp.model.StatusCode
import sttp.model.Header
import sttp.client4.Response
import java.util.Base64
import sttp.model.RequestMetadata
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

case class CachedResponse(
    body: String,
    code: StatusCode,
    statusText: String,
    headers: List[Header]
) {
  def toResponse(request: RequestMetadata): Response[Array[Byte]] = Response(
    Base64.getDecoder.decode(body),
    code,
    statusText,
    headers,
    Nil,
    request
  )
}

object CachedResponse {
  def apply(response: Response[Array[Byte]]): CachedResponse = CachedResponse(
    Base64.getEncoder.encodeToString(response.body),
    response.code,
    response.statusText,
    response.headers.toList
  )

  implicit val cachedResponseCodec: JsonValueCodec[CachedResponse] = JsonCodecMaker.make
}
