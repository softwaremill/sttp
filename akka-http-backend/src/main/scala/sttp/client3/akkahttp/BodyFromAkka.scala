package sttp.client3.akkahttp

import java.util.concurrent.atomic.AtomicBoolean
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.stream.scaladsl.{FileIO, Flow, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.util.ByteString
import akka.{Done, NotUsed}
import sttp.capabilities.akka.AkkaStreams
import sttp.client3.internal.{BodyFromResponseAs, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{
  ResponseAs,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  WebSocketResponseAs
}
import sttp.model.{Headers, ResponseMetadata}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.{WebSocket, WebSocketBufferFull, WebSocketClosed, WebSocketFrame}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure

private[akkahttp] class BodyFromAkka()(implicit ec: ExecutionContext, mat: Materializer, m: MonadError[Future]) {
  def apply[T, R](
      responseAs: ResponseAs[T, R],
      meta: ResponseMetadata,
      response: Either[HttpResponse, Promise[Flow[Message, Message, NotUsed]]]
  ): Future[T] =
    bodyFromResponseAs(responseAs, meta, response)

  private lazy val bodyFromResponseAs =
    new BodyFromResponseAs[Future, HttpResponse, Promise[Flow[Message, Message, NotUsed]], AkkaStreams.BinaryStream] {
      override protected def withReplayableBody(
          response: HttpResponse,
          replayableBody: Either[Array[Byte], SttpFile]
      ): Future[HttpResponse] = {
        val replayEntity = replayableBody match {
          case Left(byteArray) => HttpEntity(byteArray)
          case Right(file)     => HttpEntity.fromFile(response.entity.contentType, file.toFile)
        }

        Future.successful(response.copy(entity = replayEntity))
      }

      override protected def regularIgnore(response: HttpResponse): Future[Unit] = {
        // todo: Replace with HttpResponse#discardEntityBytes() once https://github.com/akka/akka-http/issues/1459 is resolved
        response.entity.dataBytes.runWith(Sink.ignore).map(_ => ())
      }

      override protected def regularAsByteArray(response: HttpResponse): Future[Array[Byte]] = {
        response.entity.dataBytes
          .runFold(ByteString(""))(_ ++ _)
          .map(_.toArray[Byte])
      }

      override protected def regularAsFile(response: HttpResponse, file: SttpFile): Future[SttpFile] = {
        val f = file.toFile
        if (!f.exists()) {
          f.getParentFile.mkdirs()
          f.createNewFile()
        }

        response.entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map(_ => file)
      }

      override protected def regularAsStream(
          response: HttpResponse
      ): Future[(Source[ByteString, Any], () => Future[Unit])] = {
        Future.successful(
          (
            response.entity.dataBytes,
            // ignoring exceptions that occur when discarding (i.e. double-materialisation exceptions)
            () => response.discardEntityBytes().future().map(_ => ()).recover { case _ => () }
          )
        )
      }

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: Promise[Flow[Message, Message, NotUsed]]
      ): Future[T] = wsFromAkka(responseAs, ws, meta)

      override protected def cleanupWhenNotAWebSocket(response: HttpResponse, e: NotAWebSocketException): Future[Unit] =
        response.entity.discardBytes().future().map(_ => ())

      override protected def cleanupWhenGotWebSocket(
          response: Promise[Flow[Message, Message, NotUsed]],
          e: GotAWebSocketException
      ): Future[Unit] = Future.successful(response.failure(e))
    }

  private def wsFromAkka[T, R](
      rr: WebSocketResponseAs[T, R],
      wsFlow: Promise[Flow[Message, Message, NotUsed]],
      meta: ResponseMetadata
  )(implicit ec: ExecutionContext, mat: Materializer): Future[T] = {
    rr match {
      case ResponseAsWebSocket(f) =>
        val (flow, wsFuture) = webSocketAndFlow(meta)
        wsFlow.success(flow)
        wsFuture.flatMap { ws =>
          val result = f.asInstanceOf[(WebSocket[Future], ResponseMetadata) => Future[T]](ws, meta)
          result.onComplete(_ => ws.close())
          result
        }
      case ResponseAsWebSocketUnsafe() =>
        val (flow, wsFuture) = webSocketAndFlow(meta)
        wsFlow.success(flow)
        wsFuture.asInstanceOf[Future[T]]
      case ResponseAsWebSocketStream(_, p) =>
        val donePromise = Promise[Done]()

        val flow = Flow[Message]
          .mapAsync(1)(messageToFrame)
          .via(p.asInstanceOf[AkkaStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
          .takeWhile {
            case WebSocketFrame.Close(_, _) => false
            case _                          => true
          }
          .mapConcat(incoming => frameToMessage(incoming).toList)
          .watchTermination() { (notUsed, done) =>
            donePromise.completeWith(done)
            notUsed
          }

        wsFlow.success(flow)

        donePromise.future.map(_ => ())
    }
  }

  private def webSocketAndFlow(meta: ResponseMetadata)(implicit
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

      override def receive(): Future[WebSocketFrame] = {
        val result = sinkQueue.pull().flatMap {
          case Some(m) => messageToFrame(m)
          case None =>
            open.set(false)
            val c = closeReceived.getAndSet(true)
            if (!c) Future.successful(WebSocketFrame.close)
            else Future.failed(WebSocketClosed(Some(WebSocketFrame.close)))
        }

        result.onComplete {
          case Failure(_) => open.set(false)
          case _          =>
        }

        result
      }

      override def send(f: WebSocketFrame, isContinuation: Boolean): Future[Unit] =
        f match {
          case WebSocketFrame.Close(_, _) =>
            val wasOpen = open.getAndSet(false)
            if (wasOpen) sourceQueue.complete()
            sourceQueue.watchCompletion().map(_ => ())

          case frame: WebSocketFrame =>
            frameToMessage(frame) match {
              case Some(m) =>
                sourceQueue.offer(m).flatMap {
                  case QueueOfferResult.Enqueued => Future.successful(())
                  case QueueOfferResult.Dropped =>
                    Future.failed(throw new IllegalStateException(WebSocketBufferFull(1)))
                  case QueueOfferResult.Failure(cause) => Future.failed(cause)
                  case QueueOfferResult.QueueClosed =>
                    Future.failed(throw new IllegalStateException(WebSocketClosed(None)))
                }
              case None => Future.successful(())
            }
        }

      override def upgradeHeaders: Headers = Headers(meta.headers)

      override def isOpen(): Future[Boolean] = Future.successful(open.get())

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

  private def frameToMessage(w: WebSocketFrame): Option[Message] = {
    w match {
      case WebSocketFrame.Text(p, _, _)   => Some(TextMessage(p))
      case WebSocketFrame.Binary(p, _, _) => Some(BinaryMessage(ByteString(p)))
      case WebSocketFrame.Ping(_)         => None
      case WebSocketFrame.Pong(_)         => None
      case WebSocketFrame.Close(_, _)     => throw WebSocketClosed(None)
    }
  }
}
