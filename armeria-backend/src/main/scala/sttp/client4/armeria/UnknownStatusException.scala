package sttp.client4.armeria

/** A `RuntimeException` raised when an `HttpStatus.UnknownStatus` received from Armeria backend. */
class UnknownStatusException(message: String) extends RuntimeException(message)
