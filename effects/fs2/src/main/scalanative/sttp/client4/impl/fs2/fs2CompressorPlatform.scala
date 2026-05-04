package sttp.client4.impl.fs2

import fs2.Stream
import fs2.io.file.Files
import cats.effect.Sync
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._

abstract class PlatformGZipFs2Compressor[F[_]: Sync: Files, R <: Fs2Streams[F]]
    extends GZipFs2Compressor[F, R] {

  override protected def compressInputStreamBody(b: java.io.InputStream): GenericRequestBody[R] =
    StreamBody(Fs2Streams[F])(compressStream(fs2.io.readInputStream(fSync.delay(b), 1024)(fSync)))

  override protected def compressFileBody(fb: FileBody): GenericRequestBody[R] =
    StreamBody(Fs2Streams[F])(compressStream(Files[F].readAll(fb.f.toPath, 1024)))
}

abstract class PlatformDeflateFs2Compressor[F[_]: Sync: Files, R <: Fs2Streams[F]]
    extends DeflateFs2Compressor[F, R] {

  override protected def compressInputStreamBody(b: java.io.InputStream): GenericRequestBody[R] =
    StreamBody(Fs2Streams[F])(compressStream(fs2.io.readInputStream(fSync.delay(b), 1024)(fSync)))

  override protected def compressFileBody(fb: FileBody): GenericRequestBody[R] =
    StreamBody(Fs2Streams[F])(compressStream(Files[F].readAll(fb.f.toPath, 1024)))
}
