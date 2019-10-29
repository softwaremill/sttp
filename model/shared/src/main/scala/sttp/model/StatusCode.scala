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
  def unsafeApply(code: Int): StatusCode = safeApply(code).getOrThrow
  def safeApply(code: Int): Either[String, StatusCode] = {
    if (code < 100 || code > 599) Left(s"Status code outside of the allowed range 100-599: $code")
    else Right(notValidated(code))
  }
  def notValidated(code: Int): StatusCode = new StatusCode(code)
}

// https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml
trait StatusCodes {
  val Continue: StatusCode = StatusCode.notValidated(100)
  val SwitchingProtocols: StatusCode = StatusCode.notValidated(101)
  val Processing: StatusCode = StatusCode.notValidated(102)
  val EarlyHints: StatusCode = StatusCode.notValidated(103)

  val Ok: StatusCode = StatusCode.notValidated(200)
  val Created: StatusCode = StatusCode.notValidated(201)
  val Accepted: StatusCode = StatusCode.notValidated(202)
  val NonAuthoritativeInformation: StatusCode = StatusCode.notValidated(203)
  val NoContent: StatusCode = StatusCode.notValidated(204)
  val ResetContent: StatusCode = StatusCode.notValidated(205)
  val PartialContent: StatusCode = StatusCode.notValidated(206)
  val MultiStatus: StatusCode = StatusCode.notValidated(207)
  val AlreadyReported: StatusCode = StatusCode.notValidated(208)
  val ImUsed: StatusCode = StatusCode.notValidated(226)

  val MultipleChoices: StatusCode = StatusCode.notValidated(300)
  val MovedPermanently: StatusCode = StatusCode.notValidated(301)
  val Found: StatusCode = StatusCode.notValidated(302)
  val SeeOther: StatusCode = StatusCode.notValidated(303)
  val NotModified: StatusCode = StatusCode.notValidated(304)
  val UseProxy: StatusCode = StatusCode.notValidated(305)
  val TemporaryRedirect: StatusCode = StatusCode.notValidated(307)
  val PermanentRedirect: StatusCode = StatusCode.notValidated(308)

  val BadRequest: StatusCode = StatusCode.notValidated(400)
  val Unauthorized: StatusCode = StatusCode.notValidated(401)
  val PaymentRequired: StatusCode = StatusCode.notValidated(402)
  val Forbidden: StatusCode = StatusCode.notValidated(403)
  val NotFound: StatusCode = StatusCode.notValidated(404)
  val MethodNotAllowed: StatusCode = StatusCode.notValidated(405)
  val NotAcceptable: StatusCode = StatusCode.notValidated(406)
  val ProxyAuthenticationRequired: StatusCode = StatusCode.notValidated(407)
  val RequestTimeout: StatusCode = StatusCode.notValidated(408)
  val Conflict: StatusCode = StatusCode.notValidated(409)
  val Gone: StatusCode = StatusCode.notValidated(410)
  val LengthRequired: StatusCode = StatusCode.notValidated(411)
  val PreconditionFailed: StatusCode = StatusCode.notValidated(412)
  val PayloadTooLarge: StatusCode = StatusCode.notValidated(413)
  val UriTooLong: StatusCode = StatusCode.notValidated(414)
  val UnsupportedMediaType: StatusCode = StatusCode.notValidated(415)
  val RangeNotSatisfiable: StatusCode = StatusCode.notValidated(416)
  val ExpectationFailed: StatusCode = StatusCode.notValidated(417)
  val MisdirectedRequest: StatusCode = StatusCode.notValidated(421)
  val UnprocessableEntity: StatusCode = StatusCode.notValidated(422)
  val Locked: StatusCode = StatusCode.notValidated(423)
  val FailedDependency: StatusCode = StatusCode.notValidated(424)
  val UpgradeRequired: StatusCode = StatusCode.notValidated(426)
  val PreconditionRequired: StatusCode = StatusCode.notValidated(428)
  val TooManyRequests: StatusCode = StatusCode.notValidated(429)
  val RequestHeaderFieldsTooLarge: StatusCode = StatusCode.notValidated(431)
  val UnavailableForLegalReasons: StatusCode = StatusCode.notValidated(451)

  val InternalServerError: StatusCode = StatusCode.notValidated(500)
  val NotImplemented: StatusCode = StatusCode.notValidated(501)
  val BadGateway: StatusCode = StatusCode.notValidated(502)
  val ServiceUnavailable: StatusCode = StatusCode.notValidated(503)
  val GatewayTimeout: StatusCode = StatusCode.notValidated(504)
  val HttpVersionNotSupported: StatusCode = StatusCode.notValidated(505)
  val VariantAlsoNegotiates: StatusCode = StatusCode.notValidated(506)
  val InsufficientStorage: StatusCode = StatusCode.notValidated(507)
  val LoopDetected: StatusCode = StatusCode.notValidated(508)
  val NotExtended: StatusCode = StatusCode.notValidated(510)
  val NetworkAuthenticationRequired: StatusCode = StatusCode.notValidated(511)
}
