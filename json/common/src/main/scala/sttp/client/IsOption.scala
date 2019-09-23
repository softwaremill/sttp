package sttp.client

trait IsOption[-T] {
  def isOption: Boolean
}

object IsOption {

  private object True extends IsOption[Any] {
    override val isOption: Boolean = true
  }

  private object False extends IsOption[Any] {
    override val isOption: Boolean = false
  }

  implicit def optionIsOption[T]: IsOption[Option[T]] = True
  implicit def leftOptionIsOption[T]: IsOption[Either[Option[T], _]] = True
  implicit def rightOptionIsOption[T]: IsOption[Either[_, Option[T]]] = True
  implicit def otherIsNotOption[T]: IsOption[T] = False

}
