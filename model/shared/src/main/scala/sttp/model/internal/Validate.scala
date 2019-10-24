package sttp.model.internal

object Validate {
  def all[T](validationErrors: Option[String]*)(result: => T): Either[String, T] = {
    validationErrors.collectFirst { case Some(e) => e } match {
      case Some(e) => Left(e)
      case None    => Right(result)
    }
  }

  def sequence[T](results: List[Either[String, T]]): Either[String, List[T]] = {
    results.collectFirst {
      case Left(e) => e
    } match {
      case Some(e) => Left(e)
      case None =>
        Right(results.collect {
          case Right(c) => c
        })
    }
  }

  implicit class RichEither[T](e: Either[String, T]) {
    def getOrThrow: T = e.fold(e => throw new IllegalArgumentException(e), identity)
  }
}
