package sttp.model

class StatusCode private (val code: Int) extends AnyVal {
  def isInformational: Boolean = code / 100 == 1
  def isSuccess: Boolean = code / 100 == 2
  def isRedirect: Boolean = code / 100 == 3
  def isClientError: Boolean = code / 100 == 4
  def isServerError: Boolean = code / 100 == 5

  override def toString: String = code.toString
}

object StatusCode extends StatusCodes {
  def apply(code: Int): StatusCode = validated(code).fold(e => throw new IllegalArgumentException(e), identity)
  def validated(code: Int): Either[String, StatusCode] = {
    if (code < 100 || code > 599) Left(s"Status code outside of the allowed range 100-599: $code")
    else Right(new StatusCode(code))
  }
  def notValidated(code: Int) = new StatusCode(code)
}

// https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml
trait StatusCodes {
  val Continue = StatusCode(100)
  val SwitchingProtocols = StatusCode(101)
  val Processing = StatusCode(102)
  val EarlyHints = StatusCode(103)

  val Ok = StatusCode(200)
  val Created = StatusCode(201)
  val Accepted = StatusCode(202)
  val NonAuthoritativeInformation = StatusCode(203)
  val NoContent = StatusCode(204)
  val ResetContent = StatusCode(205)
  val PartialContent = StatusCode(206)
  val MultiStatus = StatusCode(207)
  val AlreadyReported = StatusCode(208)
  val ImUsed = StatusCode(226)

  val MultipleChoices = StatusCode(300)
  val MovedPermanently = StatusCode(301)
  val Found = StatusCode(302)
  val SeeOther = StatusCode(303)
  val NotModified = StatusCode(304)
  val UseProxy = StatusCode(305)
  val TemporaryRedirect = StatusCode(307)
  val PermanentRedirect = StatusCode(308)

  val BadRequest = StatusCode(400)
  val Unauthorized = StatusCode(401)
  val PaymentRequired = StatusCode(402)
  val Forbidden = StatusCode(403)
  val NotFound = StatusCode(404)
  val MethodNotAllowed = StatusCode(405)
  val NotAcceptable = StatusCode(406)
  val ProxyAuthenticationRequired = StatusCode(407)
  val RequestTimeout = StatusCode(408)
  val Conflict = StatusCode(409)
  val Gone = StatusCode(410)
  val LengthRequired = StatusCode(411)
  val PreconditionFailed = StatusCode(412)
  val PayloadTooLarge = StatusCode(413)
  val UriTooLong = StatusCode(414)
  val UnsupportedMediaType = StatusCode(415)
  val RangeNotSatisfiable = StatusCode(416)
  val ExpectationFailed = StatusCode(417)
  val MisdirectedRequest = StatusCode(421)
  val UnprocessableEntity = StatusCode(422)
  val Locked = StatusCode(423)
  val FailedDependency = StatusCode(424)
  val UpgradeRequired = StatusCode(426)
  val PreconditionRequired = StatusCode(428)
  val TooManyRequests = StatusCode(429)
  val RequestHeaderFieldsTooLarge = StatusCode(431)
  val UnavailableForLegalReasons = StatusCode(451)

  val InternalServerError = StatusCode(500)
  val NotImplemented = StatusCode(501)
  val BadGateway = StatusCode(502)
  val ServiceUnavailable = StatusCode(503)
  val GatewayTimeout = StatusCode(504)
  val HttpVersionNotSupported = StatusCode(505)
  val VariantAlsoNegotiates = StatusCode(506)
  val InsufficientStorage = StatusCode(507)
  val LoopDetected = StatusCode(508)
  val NotExtended = StatusCode(510)
  val NetworkAuthenticationRequired = StatusCode(511)
}
