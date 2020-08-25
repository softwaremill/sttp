package sttp.client.akkahttp

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import akka.{Done, NotUsed}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, ResponseEntity}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{FileIO, Flow, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client.internal.{ReplayableBody, nonReplayableBody, replayableBody}
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client.{
  IgnoreResponse,
  MappedResponseAs,
  ResponseAs,
  ResponseAsBoth,
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
import sttp.model.{Headers, StatusCode}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.{WebSocket, WebSocketBufferFull, WebSocketClosed, WebSocketFrame}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure

private[akkahttp] object BodyFromAkka {
  def apply[T, R](
      responseAs: ResponseAs[T, R],
      response: Either[HttpResponse, Promise[Flow[Message, Message, NotUsed]]],
      meta: ResponseMetadata
  )(implicit ec: ExecutionContext, mat: Materializer): Future[T] =
    doApply(responseAs, response, meta).map(_._1)

  def doApply[T, R](
      responseAs: ResponseAs[T, R],
      response: Either[HttpResponse, Promise[Flow[Message, Message, NotUsed]]],
      meta: ResponseMetadata
  )(implicit ec: ExecutionContext, mat: Materializer): Future[(T, ReplayableBody)] = {
    def asByteArray(hr: HttpResponse) =
      hr.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .map(_.toArray[Byte])

    def saved(hr: HttpResponse, file: File) = {
      if (!file.exists()) {
        file.getParentFile.mkdirs()
        file.createNewFile()
      }

      hr.entity.dataBytes.runWith(FileIO.toPath(file.toPath))
    }

    (responseAs, response) match {
      case (MappedResponseAs(raw, g), _) =>
        doApply(raw, response, meta).map { case (result, replayableBody) => (g(result, meta), replayableBody) }

      case (rfm: ResponseAsFromMetadata[T, R], _) => doApply(rfm(meta), response, meta)

      case (ResponseAsBoth(l, r), _) =>
        doApply(l, response, meta).flatMap {
          case (leftResult, None) => Future.successful(((leftResult, None): T, nonReplayableBody))
          case (leftResult, Some(rb)) =>
            val replayResponse = response.left.map { r =>
              val replayEntity = rb match {
                case Left(byteArray) => HttpEntity(byteArray)
                case Right(file)     => HttpEntity.fromFile(r.entity.contentType, file.toFile)
              }

              r.copy(entity = replayEntity)
            }
            doApply(r, replayResponse, meta).map {
              case (rightResult, _) => ((leftResult, Some(rightResult)), Some(rb))
            }
        }

      case (IgnoreResponse, Left(hr)) =>
        // todo: Replace with HttpResponse#discardEntityBytes() once https://github.com/akka/akka-http/issues/1459 is resolved
        hr.entity.dataBytes.runWith(Sink.ignore).map(_ => ((), nonReplayableBody))

      case (ResponseAsByteArray, Left(hr)) =>
        asByteArray(hr).map(b => (b, replayableBody(b)))

      case (ResponseAsStream(_, f), Left(hr)) =>
        // todo: how to ensure that the stream is closed after the future returned by f completes?
        f.asInstanceOf[AkkaStreams.BinaryStream => Future[T]](hr.entity.dataBytes).map((_, nonReplayableBody))

      case (ResponseAsStreamUnsafe(_), Left(hr)) =>
        Future.successful(hr.entity.dataBytes.asInstanceOf[T]).map((_, nonReplayableBody))

      case (ResponseAsFile(file), Left(hr)) =>
        saved(hr, file.toFile).map(_ => (file, replayableBody(file)))

      case (wsr: WebSocketResponseAs[_, _], Right(promise)) =>
        wsFromAkka(wsr, promise, meta).map((_, nonReplayableBody))

      case (_: WebSocketResponseAs[_, _], Left(hr)) =>
        hr.entity
          .discardBytes()
          .future()
          .flatMap(_ =>
            Future.failed(
              new NotAWebSocketException(StatusCode(hr.status.intValue()))
            )
          )

      case (_, Right(promise)) =>
        val e = new GotAWebSocketException()
        promise.failure(e)
        Future.failed(e)
    }
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
          val result = f.asInstanceOf[WebSocket[Future] => Future[T]](ws)
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
                    Future.failed(throw new IllegalStateException(new WebSocketBufferFull(1)))
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

      override def upgradeHeaders: Headers = Headers(meta.headers)

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
