package sttp.client

import org.scalajs.dom.FormData
import org.scalajs.dom.experimental.{
  BodyInit,
  Fetch,
  HttpMethod,
  RequestCredentials,
  RequestInit,
  RequestMode,
  RequestRedirect,
  ResponseType,
  Headers => JSHeaders,
  Request => FetchRequest,
  Response => FetchResponse
}
import org.scalajs.dom.raw.{Blob, BlobPropertyBag}
import sttp.client.dom.experimental.{AbortController, FilePropertyBag, File => DomFile}
import sttp.client.internal.{SttpFile, _}
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.model.{Header, StatusCode}

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.Promise
import scala.scalajs.js.timers._
import scala.scalajs.js.typedarray._

object FetchOptions {
  val Default = FetchOptions(
    credentials = None,
    mode = None
  )
}

final case class FetchOptions(
    credentials: Option[RequestCredentials],
    mode: Option[RequestMode]
)

/**
  * A backend that uses the `fetch` JavaScript api.
  *
  * @see https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API
  */
abstract class AbstractFetchBackend[R[_], S](options: FetchOptions, customizeRequest: FetchRequest => FetchRequest)(
    monad: MonadError[R]
) extends SttpBackend[R, S, NothingT] {

  override implicit def responseMonad: MonadError[R] = monad

  override def send[T](request: Request[T, S]): R[Response[T]] = {

    // https://stackoverflow.com/q/31061838/4094860
    val readTimeout = request.options.readTimeout
    val (signal, cancelTimeout) = readTimeout match {
      case timeout: FiniteDuration =>
        val controller = new AbortController()
        val signal = controller.signal

        val timeoutHandle = setTimeout(timeout) {
          controller.abort()
        }
        (Some(signal), () => clearTimeout(timeoutHandle))

      case _ =>
        (None, () => ())

    }

    val headers = new JSHeaders()
    request.headers.foreach {
      case Header(name, value) =>
        headers.set(name, value)
    }

    val req = createBody(request.body).map { body =>
      // use manual so we can return a specific error instead of the generic "TypeError: Failed to fetch"
      val redirect = if (request.options.followRedirects) RequestRedirect.follow else RequestRedirect.manual

      val requestInitStatic = RequestInit(
        method = request.method.method.asInstanceOf[HttpMethod],
        headers = headers,
        body = body,
        credentials = options.credentials.orUndefined,
        requestRedirect = redirect,
        mode = options.mode.orUndefined
      )
      val requestInitDynamic = requestInitStatic.asInstanceOf[js.Dynamic]
      signal.foreach(s => requestInitDynamic.updateDynamic("signal")(s))
      requestInitDynamic.updateDynamic("redirect")(redirect) // named wrong in RequestInit
      val requestInit = requestInitDynamic.asInstanceOf[RequestInit]

      new FetchRequest(request.uri.toString, requestInit)
    }

    val result = req
      .flatMap { r =>
        transformPromise(Fetch.fetch(customizeRequest(r)))
      }
      .flatMap { resp =>
        if (resp.`type` == ResponseType.opaqueredirect) {
          responseMonad.error[FetchResponse](new RuntimeException("Unexpected redirect"))
        } else {
          responseMonad.unit(resp)
        }
      }
      .flatMap { resp =>
        val headers = convertResponseHeaders(resp.headers)
        val metadata = ResponseMetadata(headers, StatusCode(resp.status), resp.statusText)

        val body: R[T] = readResponseBody(resp, request.response, metadata)

        body.map { b =>
          Response[T](
            body = b,
            code = StatusCode(resp.status),
            statusText = resp.statusText,
            headers = headers,
            history = Nil
          )
        }
      }
    addCancelTimeoutHook(result, cancelTimeout)
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: NothingT[WS_RESULT]
  ): R[WebSocketResponse[WS_RESULT]] =
    handler // nothing is everything

  protected def addCancelTimeoutHook[T](result: R[T], cancel: () => Unit): R[T]

  private def convertResponseHeaders(headers: JSHeaders): Seq[Header] = {
    headers
      .jsIterator()
      .toIterator
      .flatMap { hs =>
        // this will only ever be 2 but the types dont enforce that
        if (hs.length >= 2) {
          val name = hs(0)
          hs.toList.drop(1).map(v => Header(name, v))
        } else {
          Seq.empty
        }
      }
      .toList
  }

  private def createBody(body: RequestBody[S]): R[js.UndefOr[BodyInit]] = {
    body match {
      case NoBody =>
        responseMonad.unit(js.undefined) // skip

      case b: BasicRequestBody =>
        responseMonad.unit(writeBasicBody(b))

      case StreamBody(s) =>
        handleStreamBody(s)

      case mp: MultipartBody =>
        val formData = new FormData()
        mp.parts.foreach { part =>
          val value = writeBasicBody(part.body)
          // the only way to set the content type is to use a blob
          val blob =
            value match {
              case b: Blob => b
              case v =>
                new Blob(Array(v.asInstanceOf[js.Any]).toJSArray, BlobPropertyBag(part.contentType.orUndefined))
            }
          part.fileName match {
            case None           => formData.append(part.name, blob)
            case Some(fileName) => formData.append(part.name, blob, fileName)
          }
        }
        responseMonad.unit(formData)
    }
  }

  private def writeBasicBody(body: BasicRequestBody): BodyInit = {
    body match {
      case StringBody(b, encoding, _) =>
        if (encoding.compareToIgnoreCase(Utf8) == 0) b
        else b.getBytes(encoding).toTypedArray.asInstanceOf[BodyInit]

      case ByteArrayBody(b, _) =>
        b.toTypedArray.asInstanceOf[BodyInit]

      case ByteBufferBody(b, _) =>
        b.array().toTypedArray.asInstanceOf[BodyInit]

      case InputStreamBody(is, _) =>
        toByteArray(is).toTypedArray.asInstanceOf[BodyInit]

      case FileBody(f, _) =>
        f.toDomFile
    }
  }

  protected def handleStreamBody(s: S): R[js.UndefOr[BodyInit]]

  private def readResponseBody[T](
      response: FetchResponse,
      responseAs: ResponseAs[T, S],
      meta: ResponseMetadata
  ): R[T] = {
    responseAs match {
      case MappedResponseAs(raw, g) =>
        readResponseBody(response, raw, meta).map(t => g(t, meta))

      case ResponseAsFromMetadata(f) =>
        readResponseBody(response, f(meta), meta)

      case IgnoreResponse =>
        transformPromise(response.arrayBuffer()).map(_ => ())

      case ResponseAsByteArray =>
        responseToByteArray(response)

      case ResponseAsFile(file) =>
        transformPromise(response.arrayBuffer()).map { ab =>
          SttpFile.fromDomFile(
            new DomFile(
              Array(ab.asInstanceOf[js.Any]).toJSArray,
              file.name,
              FilePropertyBag(`type` = file.toDomFile.`type`)
            )
          )
        }

      case ras @ ResponseAsStream() =>
        handleResponseAsStream(ras, response)

    }
  }

  private def responseToByteArray(response: FetchResponse) = {
    transformPromise(response.arrayBuffer()).map { ab =>
      new Int8Array(ab).toArray
    }
  }

  protected def handleResponseAsStream[T](ras: ResponseAsStream[T, S], response: FetchResponse): R[T]

  override def close(): R[Unit] = monad.unit(())

  protected def transformPromise[T](promise: => Promise[T]): R[T]

}
