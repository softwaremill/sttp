package sttp

package object client4 extends SttpApi {
  type RetryWhen = (GenericRequest[_, _], Either[Throwable, Response[_]]) => Boolean
}
