// {cat=Logging; effects=Synchronous; backend=HttpClient}: A backend wrapper which logs the response body as a string

//> using dep com.softwaremill.sttp.client4::core:4.0.9

package sttp.client4.examples.logging

import sttp.capabilities.Effect
import sttp.client4.*
import sttp.client4.wrappers.DelegateBackend

class LogAsStringBackendWrapper[F[_], P](delegate: GenericBackend[F, P]) extends DelegateBackend(delegate):
  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] =
    request match
      case r: Request[T] @unchecked =>
        request.response match
          case ra: ResponseAs[T] =>
            val r2 = r.response(asBothOption(ra, asStringAlways)).mapResponse {
              case (t, Some(s)) => println(s"Response body: $s"); t
              case (t, None)    => println("Unable to log response body"); t
            }
            delegate.send(r2)
          case other =>
            throw new IllegalStateException(s"Response-as (class: ${other.getClass}) doesn't match the request's type")
      case r: StreamRequest[T, P] @unchecked    => delegate.send(r) // no point in logging entire streaming responses
      case r: WebSocketRequest[F, T] @unchecked => delegate.send(r) // no point in logging responses of web sockets
      case r: WebSocketStreamRequest[T, P] @unchecked => delegate.send(r)

object LogAsStringBackendWrapper:
  def apply(backend: WebSocketSyncBackend): WebSocketSyncBackend =
    new LogAsStringBackendWrapper(backend) with WebSocketSyncBackend {}

  def apply[F[_], L](backend: Backend[F]): Backend[F] =
    new LogAsStringBackendWrapper(backend) with Backend[F]

  // depending on the backend & effect type used, other "apply" variants might be needed here

@main def logAsStringBackend(): Unit =
  val backend: WebSocketSyncBackend = LogAsStringBackendWrapper(DefaultSyncBackend())
  val response: Response[Either[String, String]] = basicRequest.get(uri"https://httpbin.org/get").send(backend)

  println(s"Done, response code: ${response.code}!")
