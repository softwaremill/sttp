package sttp.client4.opentelemetry.otel4s

import sttp.model.Uri

object UrlTemplates {
  private val IdPlaceholder = "{id}"
  private val IdRegex = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\d+""".r

  /** URL template function that replaces numeric IDs and UUIDs in path segments and query values with `{id}`. Always
    * returns `Some` — the template equals the original URL when no IDs are found.
    */
  val urlTemplate: Uri => Option[String] = uri => {
    val templatedSegments = uri.pathSegments.segments.map(s => if (IdRegex.matches(s.v)) IdPlaceholder else s.v)

    val templatedQueryParts = uri.querySegments.map {
      case Uri.QuerySegment.KeyValue(k, v, _, _) =>
        s"$k=${if (IdRegex.matches(v)) IdPlaceholder else v}"
      case Uri.QuerySegment.Value(v, _) =>
        if (IdRegex.matches(v)) IdPlaceholder else v
      case Uri.QuerySegment.Plain(v, _) =>
        IdRegex.replaceAllIn(v, IdPlaceholder)
    }

    val pathPart = "/" + templatedSegments.mkString("/")
    val queryPart = if (templatedQueryParts.isEmpty) "" else "?" + templatedQueryParts.mkString("&")
    Some(pathPart + queryPart)
  }
}
