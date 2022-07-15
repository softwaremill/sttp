package sttp.client3.internal

object SttpToJavaConverters {

  def toJavaFunction[U, V](f: Function1[U, V]): java.util.function.Function[U, V] =
    new java.util.function.Function[U, V] {
      override def apply(t: U): V = f(t)
    }

  def toJavaBiConsumer[U, R](f: Function2[U, R, Unit]): java.util.function.BiConsumer[U, R] =
    new java.util.function.BiConsumer[U, R] {
      override def accept(t: U, u: R): Unit = f(t, u)
    }

  def toJavaSupplier[U](f: Function0[U]): java.util.function.Supplier[U] =
    new java.util.function.Supplier[U] {
      override def get(): U = f()
    }
}
