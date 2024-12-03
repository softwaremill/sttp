package sttp.client4.internal

sealed trait ContentEncoding {
  def name: String
}

object ContentEncoding {

  val gzip = Gzip()
  val deflate = Deflate()

  case class Gzip() extends ContentEncoding {
    override def name: String = "gzip"
  }

  case class Compress() extends ContentEncoding {
    override def name: String = "compress"
  }

  case class Deflate() extends ContentEncoding {
    override def name: String = "deflate"
  }

  case class Br() extends ContentEncoding {
    override def name: String = "br"
  }

  case class Zstd() extends ContentEncoding {
    override def name: String = "zstd"
  }

}
