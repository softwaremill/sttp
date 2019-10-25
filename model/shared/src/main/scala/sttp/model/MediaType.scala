package sttp.model

import java.util.regex.Pattern

case class MediaType(mainType: String, subType: String, charset: Option[String]) {
  override def toString: String = s"$mainType/$subType" + charset.fold("")(c => s"; charset=$c")
}

object MediaType extends MediaTypes {
  def apply(mainType: String, subType: String): MediaType = MediaType(mainType, subType, None)

  // based on https://github.com/square/okhttp/blob/20cd3a0/okhttp/src/main/java/okhttp3/MediaType.kt#L94
  private val TOKEN = "([a-zA-Z0-9-!#$%&'*+.^_`{|}~]+)"
  private val QUOTED = "\"([^\"]*)\""
  private val TYPE_SUBTYPE = Pattern.compile(s"$TOKEN/$TOKEN")
  private val PARAMETER = Pattern.compile(s";\\s*(?:$TOKEN=(?:$TOKEN|$QUOTED))?")

  def parse(t: String): Either[String, MediaType] = {
    val typeSubtype = TYPE_SUBTYPE.matcher(t)
    if (!typeSubtype.lookingAt()) {
      return Left(s"""No subtype found for: "$t"""")
    }
    val mainType = typeSubtype.group(1).toLowerCase
    val subType = typeSubtype.group(2).toLowerCase

    var charset: Option[String] = None
    val parameter = PARAMETER.matcher(t)
    var s = typeSubtype.end()
    val length = t.length
    while (s < length) {
      parameter.region(s, length)
      if (!parameter.lookingAt()) {
        return Left(s"""Parameter is not formatted correctly: \"${t.substring(s)}\" for: \"$t\"""")
      }

      val name = parameter.group(1)
      if (name == null || !name.equalsIgnoreCase("charset")) {
        s = parameter.end()
      } else {
        val token = parameter.group(2)
        val charsetParameter = token match {
          case null =>
            // Value is "double-quoted". That's valid and our regex group already strips the quotes.
            parameter.group(3)
          case _ if token.startsWith("'") && token.endsWith("'") && token.length > 2 =>
            // If the token is 'single-quoted' it's invalid! But we're lenient and strip the quotes.
            token.substring(1, token.length - 1)
          case _ => token
        }
        if (charset.nonEmpty && charset.forall(charsetParameter.equalsIgnoreCase)) {
          return Left(s"""Multiple charsets defined: \"$charset\" and: \"$charsetParameter\" for: \"$t\"""")
        }
        charset = Option(charsetParameter)
        s = parameter.end()
      }
    }

    Right(MediaType(mainType, subType, charset))
  }
}

// https://www.iana.org/assignments/media-types/media-types.xhtml
trait MediaTypes {
  val ApplicationGzip = MediaType("application", "gzip")
  val ApplicationJson = MediaType("application", "json")
  val ApplicationOctetStream = MediaType("application", "octet-stream")
  val ApplicationPdf = MediaType("application", "pdf")
  val ApplicationRtf = MediaType("application", "rtf")
  val ApplicationXhtml = MediaType("application", "xhtml+xml")
  val ApplicationXml = MediaType("application", "xml")
  val ApplicationXWwwFormUrlencoded = MediaType("application", "x-www-form-urlencoded")

  val ImageGif = MediaType("image", "gif")
  val ImageJpeg = MediaType("image", "jpeg")
  val ImagePng = MediaType("image", "png")
  val ImageTiff = MediaType("image", "tiff")

  val MultipartFormData = MediaType("multipart", "form-data")
  val MultipartMixed = MediaType("multipart", "mixed")
  val MultipartAlternative = MediaType("multipart", "alternative")

  val TextCacheManifest = MediaType("text", "cache-manifest")
  val TextCalendar = MediaType("text", "calendar")
  val TextCss = MediaType("text", "css")
  val TextCsv = MediaType("text", "csv")
  val TextEventStream = MediaType("text", "event-stream")
  val TextJavascript = MediaType("text", "javascript")
  val TextHtml = MediaType("text", "html")
  val TextPlain = MediaType("text", "plain")

  val TextPlainUtf8 = MediaType("text", "plain", Some("utf-8"))
}
