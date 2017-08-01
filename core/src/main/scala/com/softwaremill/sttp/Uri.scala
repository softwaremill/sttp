package com.softwaremill.sttp

import java.net.URLEncoder

import Uri.{QueryFragment, UserInfo}
import Uri.QueryFragment.{KeyValue, Value, Plain}

import scala.annotation.tailrec
import scala.collection.immutable.Seq

/**
  * A [[https://en.wikipedia.org/wiki/Uniform_Resource_Identifier URI]].
  * All components (scheme, host, query, ...) are stored unencoded, and
  * become encoded upon serialization (using [[toString]]).
  *
  * @param queryFragments Either key-value pairs, single values, or plain
  * query fragments. Key value pairs will be serialized as `k=v`, and blocks
  * of key-value pairs/single values will be combined using `&`. Note that no
  * `&` or other separators are added around plain query fragments - if
  * required, they need to be added manually as part of the plain query
  * fragment.
  */
case class Uri(scheme: String,
               userInfo: Option[UserInfo],
               host: String,
               port: Option[Int],
               path: Seq[String],
               queryFragments: Seq[QueryFragment],
               fragment: Option[String]) {

  def scheme(s: String): Uri = this.copy(scheme = s)

  def userInfo(username: String): Uri =
    this.copy(userInfo = Some(UserInfo(username, None)))

  def userInfo(username: String, password: String): Uri =
    this.copy(userInfo = Some(UserInfo(username, Some(password))))

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
    this.copy(queryFragments = queryFragments ++ ps.map {
      case (k, v) => KeyValue(k, v)
    })
  }

  def paramsMap: Map[String, String] = paramsSeq.toMap

  def paramsSeq: Seq[(String, String)] = queryFragments.collect {
    case KeyValue(k, v, _, _) => k -> v
  }

  def queryFragment(qf: QueryFragment): Uri =
    this.copy(queryFragments = queryFragments :+ qf)

  def fragment(f: String): Uri = this.copy(fragment = Some(f))

  def fragment(f: Option[String]): Uri = this.copy(fragment = f)

  override def toString: String = {
    def encodeUserInfo(ui: UserInfo): String =
      encode(ui.username) + ui.password.fold("")(":" + encode(_))

    @tailrec
    def encodeQueryFragments(qfs: List[QueryFragment],
                             previousWasPlain: Boolean,
                             sb: StringBuilder): String = qfs match {
      case Nil => sb.toString()

      case Plain(v, re) :: t =>
        encodeQueryFragments(t,
                             previousWasPlain = true,
                             sb.append(encodeQuery(v, re)))

      case Value(v, re) :: t =>
        if (!previousWasPlain) sb.append("&")
        sb.append(encodeQuery(v, re))
        encodeQueryFragments(t, previousWasPlain = false, sb)

      case KeyValue(k, v, reK, reV) :: t =>
        if (!previousWasPlain) sb.append("&")
        sb.append(encodeQuery(k, reK)).append("=").append(encodeQuery(v, reV))
        encodeQueryFragments(t, previousWasPlain = false, sb)
    }

    val schemeS = encode(scheme)
    val userInfoS = userInfo.fold("")(encodeUserInfo(_) + "@")
    val hostS = encode(host)
    val portS = port.fold("")(":" + _)
    val pathPrefixS = if (path.isEmpty) "" else "/"
    val pathS = path.map(encode).mkString("/")
    val queryPrefixS = if (queryFragments.isEmpty) "" else "?"

    val queryS = encodeQueryFragments(queryFragments.toList,
                                      previousWasPlain = true,
                                      new StringBuilder())
    val fragS = fragment.fold("")("#" + _)
    s"$schemeS://$userInfoS$hostS$portS$pathPrefixS$pathS$queryPrefixS$queryS$fragS"
  }

  private def encode(s: Any): String = {
    // space is encoded as a +, which is only valid in the query;
    // in other contexts, it must be percent-encoded; see
    // https://stackoverflow.com/questions/2678551/when-to-encode-space-to-plus-or-20
    URLEncoder.encode(String.valueOf(s), "UTF-8").replaceAll("\\+", "%20")
  }

  private def encodeQuery(s: String, relaxed: Boolean): String =
    if (relaxed) encodeQueryRelaxed(s)
    else
      URLEncoder.encode(String.valueOf(s), "UTF-8")

  private val relaxedQueryAllowedCharacters = {
    // https://stackoverflow.com/questions/2322764/what-characters-must-be-escaped-in-an-http-query-string
    val alphanum = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet
    val special = Set('/', '?', ':', '@', '-', '.', '_', '~', '!', '$', '&',
      '\'', '(', ')', '*', '+', ',', ';', '=')
    alphanum ++ special
  }

  private def encodeQueryRelaxed(s: String): String = {
    val sb = new StringBuilder()
    // based on https://gist.github.com/teigen/5865923
    for (c <- s) {
      if (relaxedQueryAllowedCharacters(c)) sb.append(c)
      else {
        for (b <- c.toString.getBytes("UTF-8")) {
          sb.append("%")
          sb.append("%02X".format(b))
        }
      }
    }
    sb.toString
  }
}

object Uri {
  def apply(host: String): Uri =
    Uri("http", None, host, None, Vector.empty, Vector.empty, None)
  def apply(host: String, port: Int): Uri =
    Uri("http", None, host, Some(port), Vector.empty, Vector.empty, None)
  def apply(host: String, port: Int, path: Seq[String]): Uri =
    Uri("http", None, host, Some(port), path, Vector.empty, None)
  def apply(scheme: String, host: String): Uri =
    Uri(scheme, None, host, None, Vector.empty, Vector.empty, None)
  def apply(scheme: String, host: String, port: Int): Uri =
    Uri(scheme, None, host, Some(port), Vector.empty, Vector.empty, None)
  def apply(scheme: String, host: String, port: Int, path: Seq[String]): Uri =
    Uri(scheme, None, host, Some(port), path, Vector.empty, None)

  sealed trait QueryFragment
  object QueryFragment {

    /**
      * @param keyRelaxedEncoding See [[Plain.relaxedEncoding]]
      * @param valueRelaxedEncoding See [[Plain.relaxedEncoding]]
      */
    case class KeyValue(k: String,
                        v: String,
                        keyRelaxedEncoding: Boolean = false,
                        valueRelaxedEncoding: Boolean = false)
        extends QueryFragment

    /**
      * A query fragment which contains only the value, without a key.
      */
    case class Value(v: String, relaxedEncoding: Boolean = false)
        extends QueryFragment

    /**
      * A query fragment which will be inserted into the query, without and
      * preceding or following separators. Allows constructing query strings
      * which are not (only) &-separated key-value pairs.
      *
      * @param relaxedEncoding Should characters, which are allowed in the
      * query string, but normally escaped be left unchanged. These characters
      * are:
      * {{{
      * /?:@-._~!$&()*+,;=
      * }}}
      * See: [[https://stackoverflow.com/questions/2322764/what-characters-must-be-escaped-in-an-http-query-string]]
      */
    case class Plain(v: String, relaxedEncoding: Boolean = false)
        extends QueryFragment
  }

  case class UserInfo(username: String, password: Option[String])
}
