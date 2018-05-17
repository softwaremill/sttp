package com.softwaremill.sttp

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.timers._
import scala.scalajs.js.typedarray._

import com.softwaremill.sttp.dom.experimental.AbortController
import com.softwaremill.sttp.dom.experimental.FilePropertyBag
import com.softwaremill.sttp.dom.experimental.{File => DomFile}
import org.scalajs.dom.FormData
import org.scalajs.dom.experimental.BodyInit
import org.scalajs.dom.experimental.Fetch
import org.scalajs.dom.experimental.Headers
import org.scalajs.dom.experimental.HttpMethod
import org.scalajs.dom.experimental.RequestInit
import org.scalajs.dom.experimental.RequestRedirect
import org.scalajs.dom.experimental.ResponseType
import org.scalajs.dom.experimental.{Request => FetchRequest}
import org.scalajs.dom.experimental.{Response => FetchResponse}
import org.scalajs.dom.raw.Blob
import org.scalajs.dom.raw.BlobPropertyBag

object FetchOptions {
  val Default = FetchOptions(
    enableTimeouts = true
  )
}

final case class FetchOptions(
    enableTimeouts: Boolean
)

/**
  * A backend that uses the `fetch` JavaScript api.
  *
  * @see https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API
  */
class FetchBackend private (fetchOptions: FetchOptions)(implicit ec: ExecutionContext)
    extends SttpBackend[Future, Nothing] {

  @JSExport("send")
  def send_JS[T](request: Request[T, Nothing]): Promise[Response[T]] = send(request).toJSPromise

  override def send[T](request: Request[T, Nothing]): Future[Response[T]] = {

    // https://stackoverflow.com/q/31061838/4094860
    val readTimeout = request.options.readTimeout
    val (signal, cancelTimeout) = readTimeout match {
      case timeout: FiniteDuration if fetchOptions.enableTimeouts =>
        val controller = new AbortController()
        val signal = controller.signal

        val timeoutHandle = setTimeout(timeout) {
          controller.abort()
        }
        (Some(signal), () => clearTimeout(timeoutHandle))

      case _ =>
        (None, () => ())

    }

    val headers = new Headers()
    request.headers.foreach {
      case (name, value) =>
        headers.set(name, value)
    }

    // use manual so we can return a specific error instead of the generic "TypeError: Failed to fetch"
    val redirect = if (request.options.followRedirects) RequestRedirect.follow else RequestRedirect.manual

    val requestInitStatic = RequestInit(
      method = request.method.m.asInstanceOf[HttpMethod],
      headers = headers,
      body = createBody(request.body),
      requestRedirect = redirect
    )
    val requestInitDynamic = requestInitStatic.asInstanceOf[js.Dynamic]
    signal.foreach(s => requestInitDynamic.updateDynamic("signal")(s))
    requestInitDynamic.updateDynamic("redirect")(redirect) // named wrong in RequestInit
    val requestInit = requestInitDynamic.asInstanceOf[RequestInit]

    val req = new FetchRequest(request.uri.toString, requestInit)

    val result = Fetch
      .fetch(req)
      .toFuture
      .flatMap { resp =>
        if (resp.`type` == ResponseType.opaqueredirect) {
          Future.failed(new RuntimeException("Unexpected redirect"))
        } else {
          Future.successful(resp)
        }
      }
      .flatMap { resp =>
        val body = if (resp.ok) {
          readResponseBody(resp, request.response).map(Right.apply)
        } else {
          resp.text().toFuture.map(Left.apply)
        }

        body.map { b =>
          Response[T](
            body = b,
            code = resp.status,
            statusText = resp.statusText,
            headers = convertResponseHeaders(resp.headers),
            history = Nil
          )
        }
      }
    result.onComplete(_ => cancelTimeout())
    result
  }

  private def convertResponseHeaders(headers: Headers): Seq[(String, String)] = {
    headers
      .jsIterator()
      .toIterator
      .flatMap { hs =>
        if (hs.length >= 2) {
          val name = hs(0)
          hs.drop(1).map(v => (name, v))
        } else {
          Seq.empty
        }
      }
      .toList
  }

  private def createBody(body: RequestBody[Nothing]): js.UndefOr[BodyInit] = {
    body match {
      case NoBody =>
        js.undefined // skip

      case b: BasicRequestBody =>
        writeBasicBody(b)

      case StreamBody(_) =>
        // we have an instance of nothing - everything's possible!
        js.undefined

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
        formData
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

  private def readResponseBody[T](response: FetchResponse, responseAs: ResponseAs[T, Nothing]): Future[T] = {
    responseAs match {
      case MappedResponseAs(raw, g) =>
        readResponseBody(response, raw).map(g)

      case IgnoreResponse =>
        response.arrayBuffer().toFuture.map(_ => ())

      case ResponseAsString(enc) =>
        val charset = response.headers
          .get(ContentTypeHeader)
          .toOption
          .flatMap(encodingFromContentType)
          .getOrElse(enc)
        if (charset.compareToIgnoreCase(Utf8) == 0) response.text().toFuture
        else responseToByteArray(response).map(bytes => new String(bytes, charset))

      case ResponseAsByteArray =>
        responseToByteArray(response)

      case ResponseAsFile(file, _) =>
        response.arrayBuffer().toFuture.map { ab =>
          new DomFile(
            Array(ab.asInstanceOf[js.Any]).toJSArray,
            file.name,
            FilePropertyBag(`type` = file.toDomFile.`type`)
          )
        }

      case ResponseAsStream() =>
        throw new IllegalStateException()

    }
  }

  private def responseToByteArray(response: FetchResponse) = {
    response.arrayBuffer().toFuture.map { ab =>
      new Int8Array(ab).toArray
    }
  }

  override def close(): Unit = ()

  override def responseMonad: MonadError[Future] = new FutureMonad()
}

object FetchBackend {

  def apply(
      fetchOptions: FetchOptions = FetchOptions.Default
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing] =
    new FetchBackend(fetchOptions)
}
