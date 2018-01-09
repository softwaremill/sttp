package com.softwaremill.sttp

import java.net.URI
import scala.language.higherKinds

class FollowRedirectsBackend[R[_], S](delegate: SttpBackend[R, S])
    extends SttpBackend[R, S] {

  def send[T](request: Request[T, S]): R[Response[T]] = {
    sendWithCounter(request, 0)
  }

  private def sendWithCounter[T](request: Request[T, S],
                                 redirects: Int): R[Response[T]] = {
    val resp = delegate.send(request)
    if (request.options.followRedirects) {
      responseMonad.flatMap(resp) { response: Response[T] =>
        if (response.isRedirect) {
          followRedirect(request, response, redirects)
        } else {
          responseMonad.unit(response)
        }
      }
    } else {
      resp
    }
  }

  private def followRedirect[T](request: Request[T, S],
                                response: Response[T],
                                redirects: Int): R[Response[T]] = {

    response.header(LocationHeader).fold(responseMonad.unit(response)) { loc =>
      if (redirects >= FollowRedirectsBackend.MaxRedirects) {
        responseMonad.unit(
          Response(Left("Too many redirects"), 0, "", Nil, Nil))
      } else {
        followRedirect(request, response, redirects, loc)
      }
    }
  }

  private def followRedirect[T](request: Request[T, S],
                                response: Response[T],
                                redirects: Int,
                                loc: String): R[Response[T]] = {

    def isRelative(uri: String) = !uri.contains("://")

    val uri = if (isRelative(loc)) {
      // using java's URI to resolve a relative URI
      uri"${new URI(request.uri.toString).resolve(loc).toString}"
    } else {
      uri"$loc"
    }

    val redirectResponse =
      sendWithCounter(request.copy[Id, T, S](uri = uri), redirects + 1)

    responseMonad.map(redirectResponse) { rr =>
      val responseNoBody =
        response.copy(body = response.body.right.map(_ => ()))
      rr.copy(history = responseNoBody :: rr.history)
    }
  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad
}

object FollowRedirectsBackend {
  private[sttp] val MaxRedirects = 32
}
