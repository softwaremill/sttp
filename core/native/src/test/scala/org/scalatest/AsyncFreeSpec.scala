package org.scalatest

import com.softwaremill.sttp.testing.AsyncExecutionContext
import org.scalactic.{Prettifier, source}

/**
  * partially copied from org.scalatest.AsyncFreeSpecLike
  */
trait AsyncFreeSpec extends org.scalatest.Suite with AsyncExecutionContext {
  import scala.language.implicitConversions
  protected implicit def convertToFreeSpecStringWrapper(s: String)(
      implicit pos: source.Position): FreeSpecStringWrapper = new FreeSpecStringWrapper(s, pos)

  protected final class FreeSpecStringWrapper(string: String, pos: source.Position) {

    def -(fun: => Unit): Unit = in(fun)

    def in(f: => Unit): Unit = {
      try {
        f
      } catch {
        case e: exceptions.TestFailedException =>
          throw new exceptions.NotAllowedException(FailureMessages.assertionShouldBePutInsideInClauseNotDashClause,
                                                   Some(e),
                                                   e.position.getOrElse(pos))
        case e: exceptions.TestCanceledException =>
          throw new exceptions.NotAllowedException(FailureMessages.assertionShouldBePutInsideInClauseNotDashClause,
                                                   Some(e),
                                                   e.position.getOrElse(pos))
        case tgce: exceptions.TestRegistrationClosedException => throw tgce
        case e: exceptions.DuplicateTestNameException =>
          throw new exceptions.NotAllowedException(
            FailureMessages.exceptionWasThrownInDashClause(Prettifier.default,
                                                           UnquotedString(e.getClass.getName),
                                                           string,
                                                           e.getMessage),
            Some(e),
            e.position.getOrElse(pos)
          )
        case other: Throwable if (!Suite.anExceptionThatShouldCauseAnAbort(other)) =>
          throw new exceptions.NotAllowedException(
            FailureMessages.exceptionWasThrownInDashClause(Prettifier.default,
                                                           UnquotedString(other.getClass.getName),
                                                           string,
                                                           other.getMessage),
            Some(other),
            pos)
        case other: Throwable => throw other
      }
    }

  }

}
