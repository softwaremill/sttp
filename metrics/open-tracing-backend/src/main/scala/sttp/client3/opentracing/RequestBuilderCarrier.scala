package sttp.client3.opentracing

import java.util

import io.opentracing.propagation.TextMap
import sttp.client3.Request

class RequestBuilderCarrier(requestBuilder: RequestBuilderAdapter[_, _]) extends TextMap {
  override def put(key: String, value: String): Unit = requestBuilder.header(key, value)

  override def iterator(): util.Iterator[util.Map.Entry[String, String]] =
    throw new UnsupportedOperationException("carrier is write-only")
}

class RequestBuilderAdapter[T, S](var request: Request[T, S]) {
  def header(k: String, v: String): Unit = {
    request = request.header(k, v)
  }
}
