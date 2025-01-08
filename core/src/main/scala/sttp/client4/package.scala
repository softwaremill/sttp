package sttp

package object client4 extends SttpApi {

  /** The type of a predicate that can be used to determine, if a request should be retries.
    * @see
    *   [[RetryWhen.Default]]
    */
  type RetryWhen = (GenericRequest[_, _], Either[Throwable, Response[_]]) => Boolean
}
