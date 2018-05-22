package com.softwaremill.sttp

import java.net.URI
import Uri.{QueryFragment, QueryFragmentEncoding, UserInfo}
import Uri.QueryFragment.{KeyValue, Plain, Value}

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

  private val AllowedSchemeCharacters = "[a-zA-Z][a-zA-Z0-9+-.]*".r

  require(host.nonEmpty, "Host cannot be empty")
  require(AllowedSchemeCharacters.unapplySeq(scheme).isDefined,
          "Scheme can only contain alphanumeric characters, +, - and .")

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

  def toJavaUri: URI = new URI(toString())

  override def toString: String = {
    def encodeUserInfo(ui: UserInfo): String =
      encode(Rfc3986.UserInfo)(ui.username) + ui.password.fold("")(":" + encode(Rfc3986.UserInfo)(_))

    @tailrec
    def encodeQueryFragments(qfs: List[QueryFragment], previousWasPlain: Boolean, sb: StringBuilder): String =
      qfs match {
        case Nil => sb.toString()

        case Plain(v, re) :: t =>
          encodeQueryFragments(t, previousWasPlain = true, sb.append(encodeQuery(v, re)))

        case Value(v, re) :: t =>
          if (!previousWasPlain) sb.append("&")
          sb.append(encodeQuery(v, re))
          encodeQueryFragments(t, previousWasPlain = false, sb)

        case KeyValue(k, v, reK, reV) :: t =>
          if (!previousWasPlain) sb.append("&")
          sb.append(encodeQuery(k, reK)).append("=").append(encodeQuery(v, reV))
          encodeQueryFragments(t, previousWasPlain = false, sb)
      }

    val schemeS = encode(Rfc3986.Scheme)(scheme)
    val userInfoS = userInfo.fold("")(encodeUserInfo(_) + "@")
    val hostS = encodeHost
    val portS = port.fold("")(":" + _)
    val pathPrefixS = if (path.isEmpty) "" else "/"
    val pathS = path.map(encode(Rfc3986.PathSegment)).mkString("/")
    val queryPrefixS = if (queryFragments.isEmpty) "" else "?"

    val queryS = encodeQueryFragments(queryFragments.toList, previousWasPlain = true, new StringBuilder())

    // https://stackoverflow.com/questions/2053132/is-a-colon-safe-for-friendly-url-use/2053640#2053640
    val fragS = fragment.fold("")("#" + encode(Rfc3986.Fragment)(_))

    s"$schemeS://$userInfoS$hostS$portS$pathPrefixS$pathS$queryPrefixS$queryS$fragS"
  }

  private def encodeQuery(s: String, e: QueryFragmentEncoding): String =
    e match {
      case QueryFragmentEncoding.All => UriCompatibility.encodeQuery(s, "UTF-8")
      case QueryFragmentEncoding.Standard =>
        encode(Rfc3986.QueryNoStandardDelims, spaceAsPlus = true, encodePlus = true)(s)
      case QueryFragmentEncoding.Relaxed =>
        encode(Rfc3986.Query, spaceAsPlus = true)(s)
      case QueryFragmentEncoding.RelaxedWithBrackets =>
        encode(Rfc3986.QueryWithBrackets, spaceAsPlus = true)(s)
    }

  private object Rfc3986 {
    val AlphaNum: Set[Char] =
      (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet
    val Unreserved: Set[Char] = AlphaNum ++ Set('-', '.', '_', '~')
    val SubDelims: Set[Char] =
      Set('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=')
    val PChar: Set[Char] = Unreserved ++ SubDelims ++ Set(':', '@')

    val Scheme: Set[Char] = AlphaNum ++ Set('+', '-', '.')
    val UserInfo: Set[Char] = Unreserved ++ SubDelims
    val Host: Set[Char] = Unreserved ++ SubDelims
    val PathSegment: Set[Char] = PChar
    val Query: Set[Char] = PChar ++ Set('/', '?')
    val Fragment: Set[Char] = Query

    val QueryNoStandardDelims: Set[Char] = Query -- Set('&', '=')
    val QueryWithBrackets: Set[Char] = Query ++ Set('[', ']')
  }

  private val IpV6Pattern = "[0-9a-fA-F:]+".r

  private def encodeHost: String =
    host match {
      case IpV6Pattern() => s"[$host]"
      case _             => encode(Rfc3986.Host)(UriCompatibility.toASCII(host))
    }

  /**
    * @param spaceAsPlus In the query, space is encoded as a `+`. In other
    * contexts, it should be %-encoded as `%20`.
    * @param encodePlus Should `+` (which is the encoded form of space
    * in the query) be %-encoded.
    */
  private def encode(allowedCharacters: Set[Char], spaceAsPlus: Boolean = false, encodePlus: Boolean = false)(
      s: String): String = {
    val sb = new StringBuilder()
    // based on https://gist.github.com/teigen/5865923
    for (c <- s) {
      if (c == '+' && encodePlus) sb.append("%2B") // #48
      else if (allowedCharacters(c)) sb.append(c)
      else if (c == ' ' && spaceAsPlus) sb.append('+')
      else {
        for (b <- c.toString.getBytes("UTF-8")) {
          sb.append("%")
          sb.append(UriCompatibility.formatByte(b))
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
  def apply(scheme: String, host: String, path: Seq[String]): Uri =
    Uri(scheme, None, host, None, path, Vector.empty, None)
  def apply(javaUri: URI): Uri = uri"${javaUri.toString}"

  sealed trait QueryFragment
  object QueryFragment {

    /**
      * @param keyEncoding See [[Plain.encoding]]
      * @param valueEncoding See [[Plain.encoding]]
      */
    case class KeyValue(k: String,
                        v: String,
                        keyEncoding: QueryFragmentEncoding = QueryFragmentEncoding.Standard,
                        valueEncoding: QueryFragmentEncoding = QueryFragmentEncoding.Standard)
        extends QueryFragment

    /**
      * A query fragment which contains only the value, without a key.
      */
    case class Value(v: String, relaxedEncoding: QueryFragmentEncoding = QueryFragmentEncoding.Standard)
        extends QueryFragment

    /**
      * A query fragment which will be inserted into the query, without and
      * preceding or following separators. Allows constructing query strings
      * which are not (only) &-separated key-value pairs.
      *
      * @param encoding Should reserved characters (in the RFC3986 sense),
      * which are allowed in the query string, but can be also escaped be
      * left unchanged. These characters are:
      * {{{
      * /?:@-._~!$&()*+,;=
      * }}}
      * See:
      * [[https://stackoverflow.com/questions/2322764/what-characters-must-be-escaped-in-an-http-query-string]]
      * [[https://stackoverflow.com/questions/2366260/whats-valid-and-whats-not-in-a-uri-query]]
      */
    case class Plain(v: String, encoding: QueryFragmentEncoding = QueryFragmentEncoding.Standard) extends QueryFragment
  }

  sealed trait QueryFragmentEncoding
  object QueryFragmentEncoding {

    /**
      * Encodes all reserved characters using [[java.net.URLEncoder.encode()]].
      */
    case object All extends QueryFragmentEncoding

    /**
      * Encodes only the `&` and `=` reserved characters, which are usually
      * used to separate query parameter names and values.
      */
    case object Standard extends QueryFragmentEncoding

    /**
      * Doesn't encode any of the reserved characters, leaving intact all
      * characters allowed in the query string as defined by RFC3986.
      */
    case object Relaxed extends QueryFragmentEncoding

    /**
      * Doesn't encode any of the reserved characters, leaving intact all
      * characters allowed in the query string as defined by RFC3986 as well
      * as the characters `[` and `]`. These brackets aren't legal in the
      * query part of the URI, but some servers use them unencoded. See
      * https://stackoverflow.com/questions/11490326/is-array-syntax-using-square-brackets-in-url-query-strings-valid
      * for discussion.
      */
    case object RelaxedWithBrackets extends QueryFragmentEncoding
  }

  case class UserInfo(username: String, password: Option[String])
}
