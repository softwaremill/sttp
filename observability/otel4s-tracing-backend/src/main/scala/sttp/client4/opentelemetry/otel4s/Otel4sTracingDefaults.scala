package sttp.client4.opentelemetry.otel4s

import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.semconv.attributes.{
  ErrorAttributes,
  HttpAttributes,
  NetworkAttributes,
  ServerAttributes,
  UrlAttributes,
  UserAgentAttributes
}
import sttp.client4.{GenericRequest, Response}
import sttp.model.{HttpVersion, Uri}

object Otel4sTracingDefaults {

  /** @see https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name */
  def spanName(
      request: GenericRequest[_, _],
      uriTemplateClassifier: Uri => Option[Uri] = Function.const(None)
  ): String = {
    val method = request.method.method
    uriTemplateClassifier(request.uri).fold(method)(classifier => s"$method $classifier")
  }

  /** @see
    *   https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-client
    *
    * @see
    *   https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-client
    */
  def requestAttributes(
      request: GenericRequest[_, _],
      uriRedactor: Uri => Option[Uri] = redactedUserInfo,
      headersAsAttributes: Set[String] = Set.empty
  ): Attributes = {
    val b = Attributes.newBuilder

    b += HttpAttributes.HttpRequestMethod(request.method.method)
    b ++= ServerAttributes.ServerAddress.maybe(request.uri.host)
    b ++= ServerAttributes.ServerPort.maybe(request.uri.port.map(_.toLong))
    b ++= UrlAttributes.UrlFull.maybe(uriRedactor(request.uri).map(_.toString()))
    b ++= NetworkAttributes.NetworkProtocolVersion.maybe(request.httpVersion.map(networkProtocolVersion))
    b ++= UrlAttributes.UrlScheme.maybe(request.uri.scheme)
    b ++= UserAgentAttributes.UserAgentOriginal.maybe(request.header("User-Agent"))

    if (headersAsAttributes.nonEmpty) {
      b ++= request.headers
        .filter(header => headersAsAttributes.exists(header.is))
        .map { header =>
          Attribute(
            s"http.request.header.${header.name.toLowerCase}",
            Seq(header.value)
          )
        }
    }

    b.result()
  }

  /** @see https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-client */
  def responseAttributes(response: Response[_], headersAsAttributes: Set[String] = Set.empty): Attributes = {
    val b = Attributes.newBuilder

    if (!response.code.isSuccess) {
      b += ErrorAttributes.ErrorType(response.code.toString)
    }
    b += HttpAttributes.HttpResponseStatusCode(response.code.code.toLong)

    if (headersAsAttributes.nonEmpty) {
      b ++= response.headers
        .filter(header => headersAsAttributes.exists(header.is))
        .map { header =>
          Attribute(
            s"http.response.header.${header.name.toLowerCase}",
            Seq(header.value)
          )
        }
    }

    b.result()
  }

  def errorAttributes(e: Throwable): Attributes =
    Attributes(ErrorAttributes.ErrorType(e.getClass.getName))

  def redactedUserInfo(uri: Uri): Option[Uri] =
    Some(
      uri.copy(authority = uri.authority.map { authority =>
        authority.copy(userInfo = authority.userInfo.map(u => u.copy("REDACTED", u.password.map(_ => "REDACTED"))))
      })
    )

  private def networkProtocolVersion: HttpVersion => String = {
    case HttpVersion.HTTP_1   => "1.0"
    case HttpVersion.HTTP_1_1 => "1.1"
    case HttpVersion.HTTP_2   => "2"
    case HttpVersion.HTTP_3   => "3"
  }

}
