package sttp.client4.internal

import java.io.InputStream

class OnEndInputStream(delegate: InputStream, callback: () => Unit) extends InputStream {
  private var callbackCalled = false

  override def read(): Int = {
    val result = delegate.read()
    if (result == -1) onEnd()
    result
  }

  override def read(b: Array[Byte]): Int = {
    val result = delegate.read(b)
    if (result == -1) onEnd()
    result
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    val result = delegate.read(b, off, len)
    if (result == -1) onEnd()
    result
  }

  override def close(): Unit = {
    onEnd()
    delegate.close()
  }

  private def onEnd(): Unit = if (!callbackCalled) {
    callbackCalled = true
    callback()
  }
}
