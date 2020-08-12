package sttp.client.akkahttp

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import akka.{Done, NotUsed}
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{FileIO, Flow, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client.{
  IgnoreResponse,
  MappedResponseAs,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsFromMetadata,
  ResponseAsStream,
  ResponseAsStreamUnsafe,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  ResponseMetadata,
  WebSocketResponseAs
}
import sttp.model.StatusCode
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.{WebSocket, WebSocketBufferFull, WebSocketClosed, WebSocketFrame}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure

private[akkahttp] object BodyFromAkka {
  def apply[T, R](
      rr: ResponseAs[T, R],
      hr: HttpResponse,
      meta: ResponseMetadata,
      wsFlow: Option[Promise[Flow[Message, Message, NotUsed]]]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[T] = {
    def asByteArray =
      hr.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .map(_.toArray[Byte])

    def saved(file: File) = {
      if (!file.exists()) {
        file.getParentFile.mkdirs()
        file.createNewFile()
      }

      hr.entity.dataBytes.runWith(FileIO.toPath(file.toPath))
    }

    rr match {
      case MappedResponseAs(raw, g) =>
        apply(raw, hr, meta, wsFlow).map(t => g(t, meta))

      case rfm: ResponseAsFromMetadata[T, R] => apply(rfm(meta), hr, meta, wsFlow)

      case IgnoreResponse =>
        // todo: Replace with HttpResponse#discardEntityBytes() once https://github.com/akka/akka-http/issues/1459 is resolved
        hr.entity.dataBytes.runWith(Sink.ignore).map(_ => ())

      case ResponseAsByteArray =>
        asByteArray

      case ResponseAsStream(_, f) =>
        // todo: how to ensure that the stream is closed after the future returned by f completes?
        f.asInstanceOf[AkkaStreams.BinaryStream => Future[T]](hr.entity.dataBytes)

      case ResponseAsStreamUnsafe(_) =>
        Future.successful(hr.entity.dataBytes.asInstanceOf[T])

      case ResponseAsFile(file) =>
        saved(file.toFile).map(_ => file)

      case wsr: WebSocketResponseAs[_, _] =>
        wsFlow match {
          case Some(promise) => wsFromAkka(wsr, hr, promise)
          case None          => throw new IllegalStateException("Illegal WebSockets usage")
        }
    }
  }

  private def wsFromAkka[T, R](
      rr: WebSocketResponseAs[T, R],
      hr: HttpResponse,
      wsFlow: Promise[Flow[Message, Message, NotUsed]]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[T] = {
    if (hr.status.intValue() != StatusCode.SwitchingProtocols.code) {
      throw new NotAWebsocketException(hr.status.intValue())
    }

    rr match {
      case ResponseAsWebSocket(f) =>
        val (flow, wsFuture) = webSocketAndFlow()
        wsFlow.success(flow)
        wsFuture.flatMap { ws =>
          val result = f.asInstanceOf[WebSocket[Future] => Future[T]](ws)
          result.onComplete(_ => ws.close)
          result
        }
      case ResponseAsWebSocketUnsafe() =>
        val (flow, wsFuture) = webSocketAndFlow()
        wsFlow.success(flow)
        wsFuture.asInstanceOf[Future[T]]
      case ResponseAsWebSocketStream(_, p) =>
        val donePromise = Promise[Done]()

        val flow = Flow[Message]
          .mapAsync(1)(messageToFrame)
          .via(p.asInstanceOf[AkkaStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
          .mapConcat {
            case incoming: WebSocketFrame.Incoming => frameToMessage(incoming).toList
            case WebSocketFrame.Close(_, _)        => throw new WebSocketClosed()
          }
          .watchTermination() { (notUsed, done) =>
            donePromise.completeWith(done)
            notUsed
          }

        wsFlow.success(flow)

        donePromise.future.map(_ => ())
    }
  }

  private def webSocketAndFlow()(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): (Flow[Message, Message, NotUsed], Future[WebSocket[Future]]) = {
    val sinkQueuePromise = Promise[SinkQueueWithCancel[Message]]()
    val sink = Sink
      .queue[Message]()
      .mapMaterializedValue(sinkQueuePromise.success)

    val sourceQueuePromise = Promise[SourceQueueWithComplete[Message]]()
    val source =
      Source.queue[Message](1, OverflowStrategy.backpressure).mapMaterializedValue(sourceQueuePromise.success)

    val flow = Flow.fromSinkAndSource(sink, source)

    val ws = for {
      sinkQueue <- sinkQueuePromise.future
      sourceQueue <- sourceQueuePromise.future
    } yield new WebSocket[Future] {
      private val open = new AtomicBoolean(true)
      private val closeReceived = new AtomicBoolean(false)

      override def receive: Future[WebSocketFrame] = {
        val result = sinkQueue.pull().flatMap {
          case Some(m) => messageToFrame(m)
          case None =>
            open.set(false)
            val c = closeReceived.getAndSet(true)
            if (!c) Future.successful(WebSocketFrame.close)
            else Future.failed(new WebSocketClosed())
        }

        result.onComplete {
          case Failure(_) => open.set(false)
          case _          =>
        }

        result
      }

      override def send(f: WebSocketFrame, isContinuation: Boolean): Future[Unit] =
        f match {
          case incoming: WebSocketFrame.Incoming =>
            frameToMessage(incoming) match {
              case Some(m) =>
                sourceQueue.offer(m).flatMap {
                  case QueueOfferResult.Enqueued => Future.successful(())
                  case QueueOfferResult.Dropped =>
                    Future.failed(throw new IllegalStateException(new WebSocketBufferFull()))
                  case QueueOfferResult.Failure(cause) => Future.failed(cause)
                  case QueueOfferResult.QueueClosed =>
                    Future.failed(throw new IllegalStateException(new WebSocketClosed()))
                }
              case None => Future.successful(())
            }

          case WebSocketFrame.Close(_, _) =>
            val wasOpen = open.getAndSet(false)
            if (wasOpen) sourceQueue.complete()
            sourceQueue.watchCompletion().map(_ => ())
        }

      override def isOpen: Future[Boolean] = Future.successful(open.get())

      override implicit def monad: MonadError[Future] = new FutureMonad()(ec)
    }

    (flow, ws)
  }

  private def messageToFrame(
      m: Message
  )(implicit ec: ExecutionContext, mat: Materializer): Future[WebSocketFrame.Data[_]] =
    m match {
      case msg: TextMessage =>
        msg.textStream.runFold("")(_ + _).map(t => WebSocketFrame.text(t))
      case msg: BinaryMessage =>
        msg.dataStream.runFold(ByteString.empty)(_ ++ _).map(b => WebSocketFrame.binary(b.toArray))
    }

  private def frameToMessage(w: WebSocketFrame.Incoming): Option[Message] = {
    w match {
      case WebSocketFrame.Text(p, _, _)   => Some(TextMessage(p))
      case WebSocketFrame.Binary(p, _, _) => Some(BinaryMessage(ByteString(p)))
      case WebSocketFrame.Ping(_)         => None
      case WebSocketFrame.Pong(_)         => None
    }
  }
}
