package sttp.client.httpclient

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.function.UnaryOperator
import java.util.stream.{Collector, Collectors}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.capabilities.WebSockets
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.HttpClientFutureBackend.FutureEncodingHandler
import sttp.client.internal.ws.{FutureSimpleQueue, SimpleQueue}
import sttp.client.internal.{NoStreams, emptyInputStream}
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.concurrent.{ExecutionContext, Future}

class HttpClientFutureBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: FutureEncodingHandler
)(implicit ec: ExecutionContext)
    extends HttpClientAsyncBackend[Future, Nothing, WebSockets, InputStream](
      client,
      new FutureMonad,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: NoStreams = NoStreams

  override protected val bodyToHttpClient: BodyToHttpClient[Future, Nothing] = new BodyToHttpClient[Future, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[Future] = new FutureMonad
    override def streamToPublisher(stream: Nothing): Future[BodyPublisher] = stream // nothing is everything
  }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Future, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[Future, Nothing] {
      override def inputStreamToStream(is: InputStream): Future[(streams.BinaryStream, () => Future[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))
      override val streams: NoStreams = NoStreams
      override implicit def monad: MonadError[Future] = new FutureMonad()
      override def compileWebSocketPipe(
          ws: WebSocket[Future],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): Future[Unit] = pipe
    }

  override protected def createSimpleQueue[T]: Future[SimpleQueue[Future, T]] =
    Future.successful(new FutureSimpleQueue[T](None))

  override protected def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  override protected def emptyBody(): InputStream = emptyInputStream()

  override protected def publisherToBody(p: Publisher[util.List[ByteBuffer]]): InputStream = {
    val subscriber = new InputStreamSubscriber
    p.subscribe(subscriber)
    subscriber.inputStream
  }
}

object HttpClientFutureBackend {
  type FutureEncodingHandler = EncodingHandler[InputStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: FutureEncodingHandler
  )(implicit ec: ExecutionContext): SttpBackend[Future, WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientFutureBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: FutureEncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    HttpClientFutureBackend(
      HttpClientBackend.defaultClient(options),
      closeClient = true,
      customizeRequest,
      customEncodingHandler
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: FutureEncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    HttpClientFutureBackend(client, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackendStub[Future, WebSockets] =
    SttpBackendStub(new FutureMonad())
}

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
private[httpclient] class InputStreamSubscriber extends Subscriber[java.util.List[ByteBuffer]] {
  // a pair of values: (is cancelled, current subscription)
  private val subscription = new AtomicReference[(Boolean, Subscription)]((false, null))
  private val chunks = new LinkedBlockingQueue[Message]()

  val inputStream: InputStream = new InputStream {
    private val exhausted = new AtomicBoolean(false)
    private val currentBuffer: AtomicReference[Option[ByteBuffer]] = new AtomicReference[Option[ByteBuffer]](None)

    override def read(): Int = {
      if (exhausted.get()) {
        -1
      } else {
        val byteRead = currentBuffer.get() match {
          case Some(buffer) if buffer.hasRemaining =>
            buffer.get() & 0xff
          case _ =>
            chunks.take() match {
              case Message.Normal(buffer) =>
                currentBuffer.set(Some(buffer))
                buffer.get() & 0xff
              case Message.Error(ex) =>
                throw ex
              case Message.Completed() =>
                exhausted.set(true)
                -1
            }
        }
        byteRead
      }
    }
  }

  override def onSubscribe(s: Subscription): Unit = {
    assert(s != null)

    // The following can be safely run multiple times, as cancel() is idempotent
    val result = subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        // If someone has made a mistake and added this Subscriber multiple times, let's handle it gracefully
        if (current._2 != null) {
          current._2.cancel() // Cancel the additional subscription
        }

        if (current._1) { // already cancelled
          s.cancel()
          (true, null)
        } else { // happy path
          (false, s)
        }
      }
    })

    if (result._2 != null) {
      result._2.request(Long.MaxValue) // not cancelled, we can request data
    }
  }

  private val toListCollector: Collector[Message, _, util.List[Message]] = Collectors.toList()
  override def onNext(b: java.util.List[ByteBuffer]): Unit = {
    assert(b != null)
    chunks.addAll(b.stream().map(Message.normal(_)).collect(toListCollector))
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    chunks.add(Message.Error(t))
  }

  override def onComplete(): Unit = {
    chunks.add(Message.Completed())
  }

  def cancel(): Unit = {
    // subscription.cancel is idempotent:
    // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification
    // so the following can be safely retried
    subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        if (current._2 != null) current._2.cancel()
        (true, null)
      }
    })
  }
}

sealed trait Message
object Message {

  case class Normal(payload: ByteBuffer) extends Message
  case class Error(ex: Throwable) extends Message
  case class Completed() extends Message

  def normal(payload: ByteBuffer): Message = Normal(payload)
}
