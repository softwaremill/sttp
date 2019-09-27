package sttp.client.asynchttpclient

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue

import com.github.ghik.silencer.silent
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpHeaders
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.proxy.ProxyServer
import org.asynchttpclient.request.body.multipart.{ByteArrayPart, FilePart, StringPart}
import org.asynchttpclient.{
  AsyncHandler,
  AsyncHttpClient,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig,
  HttpResponseBodyPart,
  HttpResponseStatus,
  Param,
  Realm,
  RequestBuilder,
  Request => AsyncRequest,
  Response => AsyncResponse
}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.client
import sttp.client.SttpBackendOptions.ProxyType.{Http, Socks}
import sttp.client.internal._
import sttp.client.monad.{MonadAsyncError, MonadError}
import sttp.client.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  IgnoreResponse,
  InputStreamBody,
  MappedResponseAs,
  MultipartBody,
  NoBody,
  RequestBody,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsStream,
  ResponseMetadata,
  StreamBody,
  StringBody,
  SttpBackend,
  SttpBackendOptions,
  _
}
import sttp.model._

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.util.Try

abstract class AsyncHttpClientBackend[R[_], S](
    asyncHttpClient: AsyncHttpClient,
    monad: MonadAsyncError[R],
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends SttpBackend[R, S] {

  @silent("discarded")
  override def send[T](r: Request[T, S]): R[Response[T]] = {
    val preparedRequest = monad.fromTry(Try(asyncHttpClient.prepareRequest(requestToAsync(r))))

    monad.flatMap(preparedRequest) { ahcRequest =>
      monad.flatten(monad.async[R[Response[T]]] { cb =>
        def success(r: R[Response[T]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))

        customizeRequest(ahcRequest).execute(streamingAsyncHandler(r.response, success, error))
      })
    }
  }

  override def responseMonad: MonadError[R] = monad

  protected def streamBodyToPublisher(s: S): Publisher[ByteBuf]

  protected def publisherToStreamBody(p: Publisher[ByteBuffer]): S

  protected def publisherToBytes(p: Publisher[ByteBuffer]): R[Array[Byte]] = {
    monad.async { cb =>
      def success(r: ByteBuffer): Unit = cb(Right(r.array()))
      def error(t: Throwable): Unit = cb(Left(t))

      p.subscribe(new SimpleSubscriber(success, error))
    }
  }

  protected def publisherToFile(p: Publisher[ByteBuffer], f: File): R[Unit] = {
    monad.map(publisherToBytes(p))(bytes => FileHelpers.saveFile(f, new ByteArrayInputStream(bytes)))
  }

  private def streamingAsyncHandler[T](
      responseAs: ResponseAs[T, S],
      success: R[Response[T]] => Unit,
      error: Throwable => Unit
  ): AsyncHandler[Unit] = {
    new StreamedAsyncHandler[Unit] {
      private val builder = new AsyncResponse.ResponseBuilder()
      private var publisher: Option[Publisher[ByteBuffer]] = None
      private var completed = false

      override def onStream(p: Publisher[HttpResponseBodyPart]): AsyncHandler.State = {
        // Sadly we don't have .map on Publisher
        publisher = Some(new Publisher[ByteBuffer] {
          override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit =
            p.subscribe(new Subscriber[HttpResponseBodyPart] {
              override def onError(t: Throwable): Unit = s.onError(t)
              override def onComplete(): Unit = s.onComplete()
              override def onNext(t: HttpResponseBodyPart): Unit =
                s.onNext(t.getBodyByteBuffer)
              override def onSubscribe(v: Subscription): Unit =
                s.onSubscribe(v)
            })
        })
        // #2: sometimes onCompleted() isn't called, only onStream(); this
        // seems to be true esp for https sites. For these cases, completing
        // the request here.
        doComplete()
        State.CONTINUE
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.State =
        throw new IllegalStateException("Requested a streaming backend, unexpected eager body parts.")

      override def onHeadersReceived(headers: HttpHeaders): AsyncHandler.State = {
        builder.accumulate(headers)
        State.CONTINUE
      }

      override def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State = {
        builder.accumulate(responseStatus)
        State.CONTINUE
      }

      override def onCompleted(): Unit = {
        // if the request had no body, onStream() will never be called
        doComplete()
      }

      private def doComplete(): Unit = {
        if (!completed) {
          completed = true

          val baseResponse = readResponseNoBody(builder.build())
          val p = publisher.getOrElse(EmptyPublisher)
          val b = handleBody(p, responseAs, baseResponse)

          success(monad.map(b)(t => baseResponse.copy(body = t)))
        }
      }

      private def handleBody[TT](
          p: Publisher[ByteBuffer],
          r: ResponseAs[TT, _],
          responseMetadata: ResponseMetadata
      ): R[TT] =
        r match {
          case MappedResponseAs(raw, g) =>
            val nested = handleBody(p, raw, responseMetadata)
            monad.map(nested)(g(_, responseMetadata))
          case ResponseAsFromMetadata(f) => handleBody(p, f(responseMetadata), responseMetadata)
          case _: ResponseAsStream[_, _] => monad.unit(publisherToStreamBody(p).asInstanceOf[TT])
          case IgnoreResponse            =>
            // getting the body and discarding it
            monad.map(publisherToBytes(p))(_ => ())

          case ResponseAsByteArray =>
            publisherToBytes(p)

          case ResponseAsFile(file) =>
            monad.map(publisherToFile(p, file.toFile))(_ => file)
        }

      override def onThrowable(t: Throwable): Unit = {
        error(t)
      }
    }
  }

  private def requestToAsync(r: Request[_, S]): AsyncRequest = {
    val readTimeout = r.options.readTimeout
    val rb = new RequestBuilder(r.method.method)
      .setUrl(r.uri.toString)
      .setReadTimeout(if (readTimeout.isFinite) readTimeout.toMillis.toInt else -1)
      .setRequestTimeout(if (readTimeout.isFinite) readTimeout.toMillis.toInt else -1)
    r.headers.foreach { case Header(k, v) => rb.setHeader(k, v) }
    setBody(r, r.body, rb)
    rb.build()
  }

  @silent("discarded")
  private def setBody(r: Request[_, S], body: RequestBody[S], rb: RequestBuilder): Unit = {
    body match {
      case NoBody => // skip

      case StringBody(b, encoding, _) =>
        rb.setBody(b.getBytes(encoding))

      case ByteArrayBody(b, _) =>
        rb.setBody(b)

      case ByteBufferBody(b, _) =>
        rb.setBody(b)

      case InputStreamBody(b, _) =>
        rb.setBody(b)

      case FileBody(b, _) =>
        rb.setBody(b.toFile)

      case StreamBody(s) =>
        val cl = r.headers
          .find(_.is(HeaderNames.ContentLength))
          .map(_.value.toLong)
          .getOrElse(-1L)
        rb.setBody(streamBodyToPublisher(s), cl)

      case MultipartBody(ps) =>
        ps.foreach(addMultipartBody(rb, _))
    }
  }

  @silent("discarded")
  private def addMultipartBody(rb: RequestBuilder, mp: Part[BasicRequestBody]): Unit = {
    // async http client only supports setting file names on file parts. To
    // set a file name on an arbitrary part we have to use a small "work
    // around", combining the file name with the name (surrounding quotes
    // are added by ahc).
    def nameWithFilename = mp.fileName.fold(mp.name) { fn =>
      s"""${mp.name}"; filename="$fn"""
    }

    val bodyPart = mp.body match {
      case StringBody(b, encoding, _) =>
        new StringPart(nameWithFilename, b, mp.contentType.getOrElse(MediaTypes.Text), Charset.forName(encoding))
      case ByteArrayBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b)
      case ByteBufferBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b.array())
      case InputStreamBody(b, _) =>
        // sadly async http client only supports parts that are strings,
        // byte arrays or files
        new ByteArrayPart(nameWithFilename, toByteArray(b))
      case FileBody(b, _) =>
        new FilePart(mp.name, b.toFile, null, null, mp.fileName.orNull)
    }

    bodyPart.setCustomHeaders(mp.additionalHeaders.map(h => new Param(h.name, h.value)).toList.asJava)

    rb.addBodyPart(bodyPart)
  }

  private def readResponseNoBody(response: AsyncResponse): Response[Unit] = {
    client.Response(
      (),
      StatusCode(response.getStatusCode),
      response.getStatusText,
      response.getHeaders
        .iteratorAsString()
        .asScala
        .map(e => Header(e.getKey, e.getValue))
        .toList,
      Nil
    )
  }

  override def close(): R[Unit] = {
    if (closeClient) monad.eval(asyncHttpClient.close()) else monad.unit(())
  }
}

object AsyncHttpClientBackend {

  private[asynchttpclient] def defaultConfigBuilder(
      options: SttpBackendOptions
  ): DefaultAsyncHttpClientConfig.Builder = {
    val configBuilder = new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(options.connectionTimeout.toMillis.toInt)

    options.proxy match {
      case None => configBuilder
      case Some(p) =>
        val proxyType: org.asynchttpclient.proxy.ProxyType =
          p.proxyType match {
            case Socks => org.asynchttpclient.proxy.ProxyType.SOCKS_V5
            case Http  => org.asynchttpclient.proxy.ProxyType.HTTP
          }

        configBuilder.setProxyServer {
          val builder = new ProxyServer.Builder(p.host, p.port)
            .setProxyType(proxyType) // Fix issue #145
            .setNonProxyHosts(p.nonProxyHosts.asJava)

          p.auth.foreach { proxyAuth =>
            builder.setRealm(
              new Realm.Builder(proxyAuth.username, proxyAuth.password).setScheme(Realm.AuthScheme.BASIC)
            )
          }

          builder.build()
        }
    }
  }

  private[asynchttpclient] def defaultClient(options: SttpBackendOptions): AsyncHttpClient = {
    new DefaultAsyncHttpClient(defaultConfigBuilder(options).build())
  }

  private[asynchttpclient] def clientWithModifiedOptions(
      options: SttpBackendOptions,
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder
  ): AsyncHttpClient = {
    new DefaultAsyncHttpClient(updateConfig(defaultConfigBuilder(options)).build())
  }
}

private[asynchttpclient] object EmptyPublisher extends Publisher[ByteBuffer] {
  override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit = {
    s.onComplete()
  }
}

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
private[asynchttpclient] class SimpleSubscriber(success: ByteBuffer => Unit, error: Throwable => Unit)
    extends Subscriber[ByteBuffer] {
  private var subscription: Subscription = _
  private val chunks = new ConcurrentLinkedQueue[Array[Byte]]()
  private var size = 0

  override def onSubscribe(s: Subscription): Unit = {
    assert(s != null)
    // If someone has made a mistake and added this Subscriber multiple times, let's handle it gracefully
    if (this.subscription != null) {
      s.cancel() // Cancel the additional subscription

    } else {
      subscription = s
      subscription.request(Long.MaxValue)
    }
  }

  @silent("discarded")
  override def onNext(b: ByteBuffer): Unit = {
    assert(b != null)
    val a = b.array()
    size += a.length
    chunks.add(a)
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    chunks.clear()
    error(t)
  }

  override def onComplete(): Unit = {
    val result = ByteBuffer.allocate(size)
    chunks.asScala.foreach(result.put)
    chunks.clear()
    success(result)
  }
}
