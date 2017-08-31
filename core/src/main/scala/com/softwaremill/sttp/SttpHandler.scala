package com.softwaremill.sttp

import java.net.URI

import scala.language.higherKinds

/**
  * @tparam R The type constructor in which responses are wrapped. E.g. `Id`
  *           for synchronous handlers, `Future` for asynchronous handlers.
  * @tparam S The type of streams that are supported by the handler. `Nothing`,
  *           if streaming requests/responses is not supported by this handler.
  */
trait SttpHandler[R[_], -S] {
  def send[T](request: Request[T, S]): R[Response[T]] = {
    sendWithCounter(request, 0)
  }

  private def sendWithCounter[T](request: Request[T, S],
                                 redirects: Int): R[Response[T]] = {
    val resp = doSend(request)
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
      if (redirects >= SttpHandler.MaxRedirects) {
        responseMonad.unit(Response(Left("Too many redirects"), 0, Nil, Nil))
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

  def close(): Unit = {}

  protected def doSend[T](request: Request[T, S]): R[Response[T]]

  /**
    * The monad in which the responses are wrapped. Allows writing wrapper
    * handlers, which map/flatMap over the return value of [[send]].
    */
  def responseMonad: MonadError[R]
}

object SttpHandler {
  private[sttp] val MaxRedirects = 32
}
