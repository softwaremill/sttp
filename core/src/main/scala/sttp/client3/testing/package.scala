package sttp.client3

import sttp.client3.internal.toByteArray

package object testing {
  implicit class RichTestingRequest[T](r: Request[T, _]) {

    /** Force the request body into a string. If the body is a file, the file contents will be returned. If the body is
      * an input stream, the stream will be consumed. If the body is a stream / multipart, an exception will be thrown.
      */
    def forceBodyAsString: String =
      r.body match {
        case NoBody                => ""
        case StringBody(s, _, _)   => s
        case ByteArrayBody(b, _)   => new String(b)
        case ByteBufferBody(b, _)  => new String(b.array())
        case InputStreamBody(b, _) => new String(toByteArray(b))
        case FileBody(f, _)        => f.readAsString
        case StreamBody(_) =>
          throw new IllegalArgumentException("The body of this request is a stream, cannot convert to String")
        case MultipartBody(_) =>
          throw new IllegalArgumentException("The body of this request is multipart, cannot convert to String")
      }

    /** Force the request body into a string. If the body is a file, the file contents will be returned. If the body is
      * an input stream, the stream will be consumed. If the body is a stream / multipart, an exception will be thrown.
      */
    def forceBodyAsByteArray: Array[Byte] =
      r.body match {
        case NoBody                     => Array.emptyByteArray
        case StringBody(s, encoding, _) => s.getBytes(encoding)
        case ByteArrayBody(b, _)        => b
        case ByteBufferBody(b, _)       => b.array()
        case InputStreamBody(b, _)      => toByteArray(b)
        case FileBody(f, _)             => f.readAsByteArray
        case StreamBody(_) =>
          throw new IllegalArgumentException("The body of this request is a stream, cannot convert to String")
        case MultipartBody(_) =>
          throw new IllegalArgumentException("The body of this request is multipart, cannot convert to String")
      }
  }
}
