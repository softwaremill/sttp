package sttp.client

import java.nio.ByteBuffer

import org.scalajs.dom.FormData
import org.scalajs.dom.experimental.{
  AbortSignal,
  BodyInit,
  Fetch,
  HeadersInit,
  HttpMethod,
  RequestCache,
  ReferrerPolicy,
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
import sttp.client.ws.WebSocketResponse
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
abstract class AbstractFetchBackend[F[_], S](options: FetchOptions, customizeRequest: FetchRequest => FetchRequest)(
    monad: MonadError[F]
) extends SttpBackend[F, S, NothingT] {
  override implicit def responseMonad: MonadError[F] = monad

  override def send[T](request: Request[T, S]): F[Response[T]] = {
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

    val rheaders = new JSHeaders()
    request.headers.foreach {
      case Header(name, value) =>
        rheaders.set(name, value)
    }

    val req = createBody(request.body).map { rbody =>
      // use manual so we can return a specific error instead of the generic "TypeError: Failed to fetch"
      val rredirect = if (request.options.followRedirects) RequestRedirect.follow else RequestRedirect.manual

      val requestInitStatic = new RequestInit(){
        var method: js.UndefOr[HttpMethod] = request.method.method.asInstanceOf[HttpMethod]

        var headers: js.UndefOr[HeadersInit] = rheaders

        var body: js.UndefOr[BodyInit] = rbody

        var referrer: js.UndefOr[String] = js.undefined

        var referrerPolicy: js.UndefOr[ReferrerPolicy] = js.undefined

        var mode: js.UndefOr[RequestMode] = options.mode.orUndefined

        var credentials: js.UndefOr[RequestCredentials] = options.credentials.orUndefined
      
        var cache: js.UndefOr[RequestCache] = js.undefined
      
        var redirect: js.UndefOr[RequestRedirect] = rredirect
      
        var integrity: js.UndefOr[String] = js.undefined
      
        var keepalive: js.UndefOr[Boolean] = js.undefined
      
        var signal: js.UndefOr[AbortSignal] = js.undefined
      
        var window: js.UndefOr[Null] = js.undefined
      }

      val requestInitDynamic = requestInitStatic.asInstanceOf[js.Dynamic]
      signal.foreach(s => requestInitDynamic.updateDynamic("signal")(s))
      requestInitDynamic.updateDynamic("redirect")(rredirect) // named wrong in RequestInit
      val requestInit = requestInitDynamic.asInstanceOf[RequestInit]

      new FetchRequest(request.uri.toString, requestInit)
    }

    val result = req
      .flatMap { r => transformPromise(Fetch.fetch(customizeRequest(r))) }
      .flatMap { resp =>
        if (resp.`type` == ResponseType.opaqueredirect) {
          responseMonad.error[FetchResponse](new RuntimeException("Unexpected redirect"))
        } else {
          responseMonad.unit(resp)
        }
      }
      .flatMap { resp =>
        val headers = convertResponseHeaders(resp.headers)
        val metadata = ResponseMetadata(headers, StatusCode.notValidated(resp.status), resp.statusText)

        val body: F[T] = readResponseBody(resp, request.response, metadata)

        body.map { b =>
          Response[T](
            body = b,
            code = StatusCode.notValidated(resp.status),
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
  ): F[WebSocketResponse[WS_RESULT]] =
    handler // nothing is everything

  protected def addCancelTimeoutHook[T](result: F[T], cancel: () => Unit): F[T]

  private def convertResponseHeaders(headers: JSHeaders): Seq[Header] = {
    headers
      .jsIterator()
      .toIterator
      .flatMap { hs =>
        // this will only ever be 2 but the types dont enforce that
        if (hs.length >= 2) {
          val name = hs(0)
          hs.toList.drop(1).map(v => Header.notValidated(name, v))
        } else {
          Seq.empty
        }
      }
      .toList
  }

  private def createBody(body: RequestBody[S]): F[js.UndefOr[BodyInit]] = {
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
        if (b.isReadOnly) cloneByteBuffer(b).array().toTypedArray.asInstanceOf[BodyInit]
        else b.array().toTypedArray.asInstanceOf[BodyInit]

      case InputStreamBody(is, _) =>
        toByteArray(is).toTypedArray.asInstanceOf[BodyInit]

      case FileBody(f, _) =>
        f.toDomFile
    }
  }

  // https://stackoverflow.com/questions/3366925/deep-copy-duplicate-of-javas-bytebuffer
  private def cloneByteBuffer(original: ByteBuffer): ByteBuffer = {
    val clone =
      if (original.isDirect) ByteBuffer.allocateDirect(original.capacity)
      else ByteBuffer.allocate(original.capacity)
    val readOnlyCopy = original.asReadOnlyBuffer
    readOnlyCopy.rewind
    clone
      .put(original)
      .flip
      .position(original.position)
      .limit(original.limit)
      .asInstanceOf[ByteBuffer]
      .order(original.order)
  }

  protected def handleStreamBody(s: S): F[js.UndefOr[BodyInit]]

  private def readResponseBody[T](
      response: FetchResponse,
      responseAs: ResponseAs[T, S],
      meta: ResponseMetadata
  ): F[T] = {
    responseAs match {
      case MappedResponseAs(raw, g) =>
        readResponseBody(response, raw, meta).map(t => g(t, meta))

      case ResponseAsFromMetadata(f) =>
        readResponseBody(response, f(meta), meta)

      case IgnoreResponse =>
        transformPromise(response.arrayBuffer()).map(_ => ())

      case ResponseAsByteArray =>
        responseToByteArray(response).map(b => b) // adjusting type because ResponseAs is covariant

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
        handleResponseAsStream(ras, response).map(b => b) // adjusting type because ResponseAs is covariant
    }
  }

  private def responseToByteArray(response: FetchResponse) = {
    transformPromise(response.arrayBuffer()).map { ab => new Int8Array(ab).toArray }
  }

  protected def handleResponseAsStream[T](ras: ResponseAsStream[T, S], response: FetchResponse): F[T]

  override def close(): F[Unit] = monad.unit(())

  protected def transformPromise[T](promise: => Promise[T]): F[T]
}
