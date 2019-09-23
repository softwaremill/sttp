package sttp.client.model

// https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml
trait StatusCodes {
  val Continue = 100
  val SwitchingProtocols = 101
  val Processing = 102
  val EarlyHints = 103

  val Ok = 200
  val Created = 201
  val Accepted = 202
  val NonAuthoritativeInformation = 203
  val NoContent = 204
  val ResetContent = 205
  val PartialContent = 206
  val MultiStatus = 207
  val AlreadyReported = 208
  val ImUsed = 226

  val MultipleChoices = 300
  val MovedPermanently = 301
  val Found = 302
  val SeeOther = 303
  val NotModified = 304
  val UseProxy = 305
  val TemporaryRedirect = 307
  val PermanentRedirect = 308

  val BadRequest = 400
  val Unauthorized = 401
  val PaymentRequired = 402
  val Forbidden = 403
  val NotFound = 404
  val MethodNotAllowed = 405
  val NotAcceptable = 406
  val ProxyAuthenticationRequired = 407
  val RequestTimeout = 408
  val Conflict = 409
  val Gone = 410
  val LengthRequired = 411
  val PreconditionFailed = 412
  val PayloadTooLarge = 413
  val UriTooLong = 414
  val UnsupportedMediaType = 415
  val RangeNotSatisfiable = 416
  val ExpectationFailed = 417
  val MisdirectedRequest = 421
  val UnprocessableEntity = 422
  val Locked = 423
  val FailedDependency = 424
  val UpgradeRequired = 426
  val PreconditionRequired = 428
  val TooManyRequests = 429
  val RequestHeaderFieldsTooLarge = 431
  val UnavailableForLegalReasons = 451

  val InternalServerError = 500
  val NotImplemented = 501
  val BadGateway = 502
  val ServiceUnavailable = 503
  val GatewayTimeout = 504
  val HttpVersionNotSupported = 505
  val VariantAlsoNegotiates = 506
  val InsufficientStorage = 507
  val LoopDetected = 508
  val NotExtended = 510
  val NetworkAuthenticationRequired = 511
}

object StatusCodes extends StatusCodes {
  def isInformational(status: StatusCode): Boolean = status / 100 == 1
  def isSuccess(status: StatusCode): Boolean = status / 100 == 2
  def isRedirect(status: StatusCode): Boolean = status / 100 == 3
  def isClientError(status: StatusCode): Boolean = status / 100 == 4
  def isServerError(status: StatusCode): Boolean = status / 100 == 5
}
