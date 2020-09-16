package sttp.client3

import sttp.model.{HasHeaders, Header, Method, Uri}

import scala.collection.immutable.Seq

trait RequestMetadata extends HasHeaders {
  def method: Method
  def uri: Uri

  override def toString: String = s"RequestMetadata($method,$uri,${headers.map(_.toStringSafe())})"
}

object RequestMetadata {

  /**
    * Mainly useful in tests using [[sttp.client3.testing.SttpBackendStub]], when creating stub responses.
    */
  val ExampleGet: RequestMetadata = new RequestMetadata {
    override def method: Method = Method.GET
    override def uri: Uri = uri"http://example.com"
    override def headers: Seq[Header] = Nil
  }
}
