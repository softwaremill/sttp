package sttp.client3

import org.scalajs.dom.experimental.{
  AbortController,
  BodyInit,
  Fetch,
  HttpMethod,
  RequestCredentials,
  RequestInit,
  RequestMode,
  RequestRedirect,
  ResponseInit,
  ResponseType,
  Headers => JSHeaders,
  Request => FetchRequest,
  Response => FetchResponse
}
import org.scalajs.dom.raw._
import org.scalajs.dom.{FormData, WebSocket => JSWebSocket}
import sttp.capabilities.{Effect, Streams, WebSockets}
import sttp.client3.SttpClientException.ReadException
import sttp.client3.WebSocketImpl.BinaryType
import sttp.client3.dom.experimental.{FilePropertyBag, File => DomFile}
import sttp.client3.internal.ws.WebSocketEvent
import sttp.client3.internal.{SttpFile, _}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketFrame}

import java.nio.ByteBuffer
import scala.collection.immutable.Seq
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
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

/** A backend that uses the `fetch` JavaScript api.
  *
  * @see https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API
  */
abstract class AbstractFetchBackend[F[_], S <: Streams[S], P](
    options: FetchOptions,
    customizeRequest: FetchRequest => FetchRequest,
    monad: MonadError[F]
) extends SttpBackend[F, P] {
  override implicit def responseMonad: MonadError[F] = monad

  val streams: Streams[S]
  type PE = P with Effect[F] with WebSockets

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    if (request.isWebSocket) sendWebSocket(request) else sendRegular(request)

  private def sendRegular[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
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
    request.headers.foreach { header =>
      // for multipart/form-data requests dom.FormData is responsible for setting the Content-Type header
      // as it will also compute and set the boundary for the different parts, so we have to leave it out here
      if (header.is(HeaderNames.ContentType) && header.value.toLowerCase.startsWith("multipart/")) {
        if (!header.value.toLowerCase.startsWith(MediaType.MultipartFormData.toString))
          throw new IllegalArgumentException("Multipart bodies other than multipart/form-data are not supported")
      } else {
        rheaders.set(header.name, header.value)
      }
    }

    val req = createBody(request.body).map { rbody =>
      // use manual so we can return a specific error instead of the generic "TypeError: Failed to fetch"
      val rredirect = if (request.options.followRedirects) RequestRedirect.follow else RequestRedirect.manual
      val rsignal = signal.orUndefined

      val requestInitStatic = new RequestInit() {
        this.method = request.method.method.asInstanceOf[HttpMethod]
        this.headers = rheaders
        this.body = rbody
        this.referrer = js.undefined
        this.referrerPolicy = js.undefined
        this.mode = options.mode.orUndefined
        this.credentials = options.credentials.orUndefined
        this.cache = js.undefined
        this.redirect = rredirect
        this.integrity = js.undefined
        this.keepalive = js.undefined
        this.signal = rsignal
        this.window = js.undefined
      }

      val requestInitDynamic = requestInitStatic.asInstanceOf[js.Dynamic]
      signal.foreach(s => requestInitDynamic.updateDynamic("signal")(s))
      requestInitDynamic.updateDynamic("redirect")(rredirect) // named wrong in RequestInit
      val requestInit = requestInitDynamic.asInstanceOf[RequestInit]

      new FetchRequest(request.uri.toString, requestInit)
    }

    val result = req
      .flatMap { r => convertFromFuture(Fetch.fetch(customizeRequest(r)).toFuture) }
      .flatMap { resp =>
        if (resp.`type` == ResponseType.opaqueredirect) {
          responseMonad.error[FetchResponse](new RuntimeException("Unexpected redirect"))
        } else {
          responseMonad.unit(resp)
        }
      }
      .flatMap { resp =>
        val headers = convertResponseHeaders(resp.headers)
        val metadata = ResponseMetadata(StatusCode(resp.status), resp.statusText, headers)

        val body: F[T] = bodyFromResponseAs(request.response, metadata, Left(resp))

        body.map { b =>
          Response[T](
            body = b,
            code = StatusCode(resp.status),
            statusText = resp.statusText,
            headers = headers,
            history = Nil,
            request = request.onlyMetadata
          )
        }
      }
    addCancelTimeoutHook(result, cancelTimeout)
  }

  protected def addCancelTimeoutHook[T](result: F[T], cancel: () => Unit): F[T]

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

  private def createBody[R >: PE](body: RequestBody[R]): F[js.UndefOr[BodyInit]] = {
    body match {
      case NoBody =>
        responseMonad.unit(js.undefined) // skip

      case b: BasicRequestBody =>
        responseMonad.unit(writeBasicBody(b))

      case StreamBody(s) =>
        handleStreamBody(s.asInstanceOf[streams.BinaryStream])

      case mp: MultipartBody[_] =>
        val formData = new FormData()
        mp.parts.foreach { part =>
          val value = part.body match {
            case NoBody                 => Array[Byte]().toTypedArray.asInstanceOf[BodyInit]
            case body: BasicRequestBody => writeBasicBody(body)
            case StreamBody(_)          => throw new IllegalArgumentException("Streaming multipart bodies are not supported")
            case MultipartBody(_)       => throwNestedMultipartNotAllowed
          }
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
        byteBufferToArray(b).toTypedArray.asInstanceOf[BodyInit]

      case InputStreamBody(is, _) =>
        toByteArray(is).toTypedArray.asInstanceOf[BodyInit]

      case FileBody(f, _) =>
        f.toDomFile
    }
  }

  // https://stackoverflow.com/questions/679298/gets-byte-array-from-a-bytebuffer-in-java
  private def byteBufferToArray(bb: ByteBuffer): Array[Byte] = {
    val b = new Array[Byte](bb.remaining())
    bb.get(b)
    b
  }

  private def sendWebSocket[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    val queue = new JSSimpleQueue[F, WebSocketEvent]
    val ws = new JSWebSocket(request.uri.toString)
    ws.binaryType = BinaryType

    val isOpen = Promise[Unit]()

    ws.onopen = (_: Event) => {
      isOpen.success(())
      queue.offer(WebSocketEvent.Open())
    }
    ws.onmessage = (event: MessageEvent) => queue.offer(toWebSocketEvent(event))
    ws.onerror = (_: Event) => {
      val msg = "Something went wrong in web socket or it could not be opened"
      if (!isOpen.isCompleted) isOpen.failure(new ReadException(request, new RuntimeException(msg)))
      else queue.offer(WebSocketEvent.Error(new RuntimeException(msg)))
    }
    ws.onclose = (event: CloseEvent) => queue.offer(toWebSocketEvent(event))

    convertFromFuture(isOpen.future).flatMap { _ =>
      val webSocket = WebSocketImpl.newJSCoupledWebSocket(ws, queue)
      bodyFromResponseAs
        .apply(request.response, ResponseMetadata(StatusCode.Ok, "", request.headers), Right(webSocket))
        .map(Response.ok)
    }
  }

  private def toWebSocketEvent(msg: MessageEvent): WebSocketEvent =
    msg.data match {
      case payload: ArrayBuffer =>
        val dv = new DataView(payload)
        val bytes = new Array[Byte](dv.byteLength)
        0 until dv.byteLength foreach { i => bytes(i) = dv.getInt8(i) }
        WebSocketEvent.Frame(WebSocketFrame.binary(bytes))
      case payload: String => WebSocketEvent.Frame(WebSocketFrame.text(payload))
      case _               => throw new RuntimeException(s"Unknown format of event.data ${msg.data}")
    }

  private def toWebSocketEvent(close: CloseEvent): WebSocketEvent =
    WebSocketEvent.Frame(WebSocketFrame.Close(close.code, close.reason))

  protected def handleStreamBody(s: streams.BinaryStream): F[js.UndefOr[BodyInit]]

  private lazy val bodyFromResponseAs = new BodyFromResponseAs[F, FetchResponse, WebSocket[F], streams.BinaryStream]() {

    override protected def withReplayableBody(
        response: FetchResponse,
        replayableBody: Either[Array[Byte], SttpFile]
    ): F[FetchResponse] = {
      val bytes = replayableBody match {
        case Left(byteArray) => byteArray
        case Right(_)        => throw new IllegalArgumentException("Replayable file bodies are not supported")
      }
      new FetchResponse(bytes.toTypedArray.asInstanceOf[BodyInit], response.asInstanceOf[ResponseInit]).unit
    }

    override protected def regularIgnore(response: FetchResponse): F[Unit] =
      convertFromFuture(response.arrayBuffer().toFuture).map(_ => ())

    override protected def regularAsByteArray(response: FetchResponse): F[Array[Byte]] =
      convertFromFuture(response.arrayBuffer().toFuture).map { ab => new Int8Array(ab).toArray }

    override protected def regularAsFile(response: FetchResponse, file: SttpFile): F[SttpFile] =
      convertFromFuture(response.arrayBuffer().toFuture)
        .map { ab =>
          SttpFile.fromDomFile(
            new DomFile(
              Array(ab.asInstanceOf[js.Any]).toJSArray,
              file.name,
              FilePropertyBag(`type` = file.toDomFile.`type`)
            )
          )
        }

    override protected def regularAsStream(response: FetchResponse): F[(streams.BinaryStream, () => F[Unit])] =
      handleResponseAsStream(response)

    override protected def handleWS[T](
        responseAs: WebSocketResponseAs[T, _],
        meta: ResponseMetadata,
        ws: WebSocket[F]
    ): F[T] =
      responseAs match {
        case ResponseAsWebSocket(f) =>
          f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[T]].apply(ws, meta)
        case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[T]]
        case ResponseAsWebSocketStream(_, pipe) =>
          compileWebSocketPipe(ws, pipe.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
      }

    override protected def cleanupWhenNotAWebSocket(response: FetchResponse, e: NotAWebSocketException): F[Unit] =
      monad.unit(())

    override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
      monad.unit(response.close())
  }

  protected def handleResponseAsStream(response: FetchResponse): F[(streams.BinaryStream, () => F[Unit])]

  protected def compileWebSocketPipe(
      ws: WebSocket[F],
      pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): F[Unit]

  override def close(): F[Unit] = monad.unit(())

  implicit def convertFromFuture: ConvertFromFuture[F]
}
