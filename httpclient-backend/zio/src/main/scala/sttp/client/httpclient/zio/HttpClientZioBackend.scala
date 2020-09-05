package sttp.client.httpclient.zio

import java.io.InputStream
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer

import org.reactivestreams.FlowAdapters
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.{HttpClientAsyncBackend, HttpClientBackend, WebSocketHandler, zio}
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import _root_.zio._
import _root_.zio.blocking.Blocking
import _root_.zio.interop.reactivestreams._
import _root_.zio.stream.{Stream, ZStream}

import scala.util.{Success, Try}

class HttpClientZioBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler,
    chunkSize: Int
) extends HttpClientAsyncBackend[BlockingTask, ZStream[Blocking, Throwable, Byte]](
      client,
      new RIOMonadAsyncError[Blocking],
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override def streamToRequestBody(
      stream: ZStream[Blocking, Throwable, Byte]
  ): BlockingTask[HttpRequest.BodyPublisher] = {
    val publisher = stream.mapChunks(byteChunk => Chunk(ByteBuffer.wrap(byteChunk.toArray))).toPublisher
    publisher.map { pub =>
      BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(pub))
    }
  }

  override def responseBodyToStream(responseBody: InputStream): Try[ZStream[Blocking, Throwable, Byte]] = {
    Success(Stream.fromInputStreamEffect(ZIO.succeed(responseBody), chunkSize))
  }
}

object HttpClientZioBackend {

  private val defaultChunkSize = 65536

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler,
      chunkSize: Int
  ): SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler] =
    new FollowRedirectsBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler](
      new HttpClientZioBackend(
        client,
        closeClient,
        customizeRequest,
        customEncodingHandler,
        chunkSize
      )
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): Task[SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler]] =
    Task.effect(
      HttpClientZioBackend(
        HttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        customEncodingHandler,
        chunkSize
      )
    )

  def managed(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZManaged[Blocking, Throwable, SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler]] =
    ZManaged.make(apply(options, customizeRequest, customEncodingHandler, chunkSize))(
      _.close().ignore
    )

  def layer(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZLayer[Blocking, Throwable, SttpClient] = {
    ZLayer.fromManaged(
      (for {
        backend <- HttpClientZioBackend(
          options,
          customizeRequest,
          customEncodingHandler,
          chunkSize
        )
      } yield backend).toManaged(_.close().ignore)
    )
  }

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler] =
    HttpClientZioBackend(
      client,
      closeClient = false,
      customizeRequest,
      customEncodingHandler,
      chunkSize
    )

  def layerUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZLayer[Blocking, Throwable, SttpClient] = {
    ZLayer.fromManaged(
      ZManaged
        .makeEffect(
          usingClient(
            client,
            customizeRequest,
            customEncodingHandler,
            chunkSize
          )
        )(_.close().ignore)
    )
  }

  /**
    * Create a stub backend for testing, which uses the [[BlockingTask]] response wrapper, and supports
    * `Stream[Throwable, ByteBuffer]` streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler] =
    SttpBackendStub(new RIOMonadAsyncError[Blocking])

  val stubLayer: ZLayer[Any,
                        Nothing,
                        Has[zio.SttpClientStubbing.Service] with Has[
                          SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler]
                        ]] =
    SttpClientStubbing.layer
}
