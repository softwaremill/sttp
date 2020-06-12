package sttp.client.httpclient.zio

import java.io.InputStream
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer

import org.reactivestreams.FlowAdapters
import sttp.client.NothingT
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.{HttpClientAsyncBackend, HttpClientBackend}
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import zio._
import zio.blocking.Blocking
import zio.interop.reactivestreams._
import zio.stream.{Stream, ZStream}

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
  ): SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT] =
    new FollowRedirectsBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT](
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
  ): Task[SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]] =
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
  ): ZManaged[Blocking, Throwable, SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]] =
    ZManaged.make(apply(options, customizeRequest, customEncodingHandler, chunkSize))(
      _.close().ignore
    )

  def layer(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZLayer[Blocking, Throwable, Has[SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]]] = {
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
  ): SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT] =
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
  ): ZLayer[Blocking, Throwable, Has[SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]]] = {
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
  def stub: SttpBackendStub[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT] =
    SttpBackendStub(new RIOMonadAsyncError[Blocking])
}
