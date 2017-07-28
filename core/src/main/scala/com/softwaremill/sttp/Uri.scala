package com.softwaremill.sttp

import java.net.URLEncoder

import com.softwaremill.sttp.QueryFragment.{KeyValue, Plain}

import scala.collection.immutable.Seq

case class Uri(scheme: String,
               host: String,
               port: Option[Int],
               path: Seq[String],
               queryFragments: Seq[QueryFragment],
               fragment: Option[String]) {

  def scheme(s: String): Uri = this.copy(scheme = s)

  def host(h: String): Uri = this.copy(host = h)

  def port(p: Int): Uri = this.copy(port = Some(p))

  def port(p: Option[Int]): Uri = this.copy(port = p)

  def path(p: String): Uri = {
    // removing the leading slash, as it is added during serialization anyway
    val pWithoutLeadingSlash = if (p.startsWith("/")) p.substring(1) else p
    val ps = pWithoutLeadingSlash.split("/", -1).toList
    this.copy(path = ps)
  }

  def path(p1: String, p2: String, ps: String*): Uri =
    this.copy(path = p1 :: p2 :: ps.toList)

  def path(ps: scala.collection.Seq[String]): Uri = this.copy(path = ps.toList)

  /**
    * Adds the given parameter to the query.
    */
  def param(k: String, v: String): Uri = params(k -> v)

  /**
    * Adds the given parameters to the query.
    */
  def params(ps: Map[String, String]): Uri = params(ps.toSeq: _*)

  /**
    * Adds the given parameters to the query.
    */
  def params(ps: (String, String)*): Uri = {
    this.copy(queryFragments = queryFragments ++ ps.map(KeyValue.tupled))
  }

  def paramsMap: Map[String, String] = paramsSeq.toMap

  def paramsSeq: Seq[(String, String)] = queryFragments.collect {
    case KeyValue(k, v) => k -> v
  }

  def fragment(f: String): Uri = this.copy(fragment = Some(f))

  def fragment(f: Option[String]): Uri = this.copy(fragment = f)

  override def toString: String = {
    val schemeS = encode(scheme)
    val hostS = encode(host)
    val portS = port.fold("")(":" + _)
    val pathPrefixS = if (path.isEmpty) "" else "/"
    val pathS = path.map(encode).mkString("/")
    val queryPrefixS = if (queryFragments.isEmpty) "" else "?"
    val queryS = queryFragments
      .map {
        case KeyValue(k, v)   => encodeQuery(k) + "=" + encodeQuery(v)
        case Plain(v, encode) => if (encode) encodeQuery(v) else v
      }
      .mkString("&")
    val fragS = fragment.fold("")("#" + _)
    s"$schemeS://$hostS$portS$pathPrefixS$pathS$queryPrefixS$queryS$fragS"
  }

  private def encode(s: Any): String = {
    // space is encoded as a +, which is only valid in the query;
    // in other contexts, it must be percent-encoded; see
    // https://stackoverflow.com/questions/2678551/when-to-encode-space-to-plus-or-20
    URLEncoder.encode(String.valueOf(s), "UTF-8").replaceAll("\\+", "%20")
  }

  private def encodeQuery(s: Any): String =
    URLEncoder.encode(String.valueOf(s), "UTF-8")
}

sealed trait QueryFragment
object QueryFragment {
  case class KeyValue(k: String, v: String) extends QueryFragment
  case class Plain(v: String, encode: Boolean) extends QueryFragment
}
