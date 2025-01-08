package sttp.client4.caching

import sttp.model.StatusCode
import sttp.model.Header
import sttp.client4.Response
import sttp.model.Method
import java.util.Base64
import sttp.model.RequestMetadata
import sttp.model.Uri
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

case class CachedResponse(
    body: String,
    code: StatusCode,
    statusText: String,
    headers: List[Header],
    requestMethod: Method,
    requestUri: String,
    requestHeaders: List[Header]
) {
  def toResponse: Response[Array[Byte]] = Response(
    Base64.getDecoder.decode(body),
    code,
    statusText,
    headers,
    Nil,
    RequestMetadata(requestMethod, Uri.unsafeParse(requestUri), requestHeaders)
  )
}

object CachedResponse {
  def apply(response: Response[Array[Byte]]): CachedResponse = CachedResponse(
    Base64.getEncoder.encodeToString(response.body),
    response.code,
    response.statusText,
    response.headers.toList,
    response.request.method,
    response.request.uri.toString,
    response.request.headers.toList
  )

  implicit val cachedResponseCodec: JsonValueCodec[CachedResponse] = JsonCodecMaker.make
}
