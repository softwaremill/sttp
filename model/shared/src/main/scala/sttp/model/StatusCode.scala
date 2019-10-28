package sttp.model

import internal.Validate._

class StatusCode private (val code: Int) extends AnyVal {
  def isInformational: Boolean = code / 100 == 1
  def isSuccess: Boolean = code / 100 == 2
  def isRedirect: Boolean = code / 100 == 3
  def isClientError: Boolean = code / 100 == 4
  def isServerError: Boolean = code / 100 == 5

  override def toString: String = code.toString
}

object StatusCode extends StatusCodes {

  /**
    * @throws IllegalArgumentException If the status code is out of range.
    */
  def unsafeApply(code: Int): StatusCode = validated(code).getOrThrow
  def validated(code: Int): Either[String, StatusCode] = {
    if (code < 100 || code > 599) Left(s"Status code outside of the allowed range 100-599: $code")
    else Right(notValidated(code))
  }
  def notValidated(code: Int): StatusCode = new StatusCode(code)
}

// https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml
trait StatusCodes {
  val Continue: StatusCode = StatusCode.unsafeApply(100)
  val SwitchingProtocols: StatusCode = StatusCode.unsafeApply(101)
  val Processing: StatusCode = StatusCode.unsafeApply(102)
  val EarlyHints: StatusCode = StatusCode.unsafeApply(103)

  val Ok: StatusCode = StatusCode.unsafeApply(200)
  val Created: StatusCode = StatusCode.unsafeApply(201)
  val Accepted: StatusCode = StatusCode.unsafeApply(202)
  val NonAuthoritativeInformation: StatusCode = StatusCode.unsafeApply(203)
  val NoContent: StatusCode = StatusCode.unsafeApply(204)
  val ResetContent: StatusCode = StatusCode.unsafeApply(205)
  val PartialContent: StatusCode = StatusCode.unsafeApply(206)
  val MultiStatus: StatusCode = StatusCode.unsafeApply(207)
  val AlreadyReported: StatusCode = StatusCode.unsafeApply(208)
  val ImUsed: StatusCode = StatusCode.unsafeApply(226)

  val MultipleChoices: StatusCode = StatusCode.unsafeApply(300)
  val MovedPermanently: StatusCode = StatusCode.unsafeApply(301)
  val Found: StatusCode = StatusCode.unsafeApply(302)
  val SeeOther: StatusCode = StatusCode.unsafeApply(303)
  val NotModified: StatusCode = StatusCode.unsafeApply(304)
  val UseProxy: StatusCode = StatusCode.unsafeApply(305)
  val TemporaryRedirect: StatusCode = StatusCode.unsafeApply(307)
  val PermanentRedirect: StatusCode = StatusCode.unsafeApply(308)

  val BadRequest: StatusCode = StatusCode.unsafeApply(400)
  val Unauthorized: StatusCode = StatusCode.unsafeApply(401)
  val PaymentRequired: StatusCode = StatusCode.unsafeApply(402)
  val Forbidden: StatusCode = StatusCode.unsafeApply(403)
  val NotFound: StatusCode = StatusCode.unsafeApply(404)
  val MethodNotAllowed: StatusCode = StatusCode.unsafeApply(405)
  val NotAcceptable: StatusCode = StatusCode.unsafeApply(406)
  val ProxyAuthenticationRequired: StatusCode = StatusCode.unsafeApply(407)
  val RequestTimeout: StatusCode = StatusCode.unsafeApply(408)
  val Conflict: StatusCode = StatusCode.unsafeApply(409)
  val Gone: StatusCode = StatusCode.unsafeApply(410)
  val LengthRequired: StatusCode = StatusCode.unsafeApply(411)
  val PreconditionFailed: StatusCode = StatusCode.unsafeApply(412)
  val PayloadTooLarge: StatusCode = StatusCode.unsafeApply(413)
  val UriTooLong: StatusCode = StatusCode.unsafeApply(414)
  val UnsupportedMediaType: StatusCode = StatusCode.unsafeApply(415)
  val RangeNotSatisfiable: StatusCode = StatusCode.unsafeApply(416)
  val ExpectationFailed: StatusCode = StatusCode.unsafeApply(417)
  val MisdirectedRequest: StatusCode = StatusCode.unsafeApply(421)
  val UnprocessableEntity: StatusCode = StatusCode.unsafeApply(422)
  val Locked: StatusCode = StatusCode.unsafeApply(423)
  val FailedDependency: StatusCode = StatusCode.unsafeApply(424)
  val UpgradeRequired: StatusCode = StatusCode.unsafeApply(426)
  val PreconditionRequired: StatusCode = StatusCode.unsafeApply(428)
  val TooManyRequests: StatusCode = StatusCode.unsafeApply(429)
  val RequestHeaderFieldsTooLarge: StatusCode = StatusCode.unsafeApply(431)
  val UnavailableForLegalReasons: StatusCode = StatusCode.unsafeApply(451)

  val InternalServerError: StatusCode = StatusCode.unsafeApply(500)
  val NotImplemented: StatusCode = StatusCode.unsafeApply(501)
  val BadGateway: StatusCode = StatusCode.unsafeApply(502)
  val ServiceUnavailable: StatusCode = StatusCode.unsafeApply(503)
  val GatewayTimeout: StatusCode = StatusCode.unsafeApply(504)
  val HttpVersionNotSupported: StatusCode = StatusCode.unsafeApply(505)
  val VariantAlsoNegotiates: StatusCode = StatusCode.unsafeApply(506)
  val InsufficientStorage: StatusCode = StatusCode.unsafeApply(507)
  val LoopDetected: StatusCode = StatusCode.unsafeApply(508)
  val NotExtended: StatusCode = StatusCode.unsafeApply(510)
  val NetworkAuthenticationRequired: StatusCode = StatusCode.unsafeApply(511)
}
