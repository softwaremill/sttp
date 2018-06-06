package com.softwaremill.sttp

import java.net.URI

import scala.language.higherKinds

class FollowRedirectsBackend[R[_], S](delegate: SttpBackend[R, S]) extends SttpBackend[R, S] {

  def send[T](request: Request[T, S]): R[Response[T]] = {
    sendWithCounter(request, 0)
  }

  private def sendWithCounter[T](request: Request[T, S], redirects: Int): R[Response[T]] = {
    // if there are nested follow redirect backends, disabling them and handling redirects here
    val resp = delegate.send(request.followRedirects(false))
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

  private def followRedirect[T](request: Request[T, S], response: Response[T], redirects: Int): R[Response[T]] = {

    response.header(HeaderNames.Location).fold(responseMonad.unit(response)) { loc =>
      if (redirects >= request.options.maxRedirects) {
        responseMonad.unit(Response(Left("Too many redirects".getBytes(Utf8)), 0, "", Nil, Nil))
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
        response.copy(rawErrorBody = response.rawErrorBody.right.map(_ => ()))
      rr.copy(history = responseNoBody :: rr.history)
    }
  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad
}

object FollowRedirectsBackend {
  private[sttp] val MaxRedirects = 32
}
