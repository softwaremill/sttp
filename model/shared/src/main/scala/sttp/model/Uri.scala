package sttp.model

import java.net.URI

import sttp.model.Uri.QuerySegment.{KeyValue, Plain, Value}
import sttp.model.Uri.{FragmentSegment, HostSegment, PathSegment, QuerySegment, Segment, UserInfo}
import sttp.model.internal.{Rfc3986, UriCompatibility, Validate}
import sttp.model.internal.Validate._

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}
import sttp.model.internal.Rfc3986.encode

/**
  * A [[https://en.wikipedia.org/wiki/Uniform_Resource_Identifier URI]].
  * All components (scheme, host, query, ...) are stored decoded, and
  * become encoded upon serialization (using [[toString]]).
  *
  * Instances can be created using the uri interpolator: `uri"..."` (see [[UriInterpolator]]), or the factory methods
  * on the [[Uri]] companion object.
  *
  * @param querySegments Either key-value pairs, single values, or plain
  * query segments. Key value pairs will be serialized as `k=v`, and blocks
  * of key-value pairs/single values will be combined using `&`. Note that no
  * `&` or other separators are added around plain query segments - if
  * required, they need to be added manually as part of the plain query
  * segment.
  */
case class Uri private (
    scheme: String,
    userInfo: Option[UserInfo],
    hostSegment: Segment,
    port: Option[Int],
    pathSegments: Seq[Segment],
    querySegments: Seq[QuerySegment],
    fragmentSegment: Option[Segment]
) {

  /**
    * Replace the scheme. Does not validate the new scheme value.
    */
  def scheme(s: String): Uri = this.copy(scheme = s)

  def userInfo(username: String): Uri =
    this.copy(userInfo = Some(UserInfo(username, None)))

  def userInfo(username: String, password: String): Uri =
    this.copy(userInfo = Some(UserInfo(username, Some(password))))

  /**
    * Replace the host. Does not validate the new host value if it's nonempty.
    */
  def host(h: String): Uri = hostSegment(HostSegment(h))

  /**
    * Replace the host. Does not validate the new host value if it's nonempty.
    */
  def hostSegment(s: Segment): Uri = this.copy(hostSegment = s)

  def host: String = hostSegment.v

  //

  def port(p: Int): Uri = this.copy(port = Some(p))

  def port(p: Option[Int]): Uri = this.copy(port = p)

  //

  /**
    * Replace path with the given single-segment path.
    */
  def path(p: String): Uri = {
    // removing the leading slash, as it is added during serialization anyway
    val pWithoutLeadingSlash = if (p.startsWith("/")) p.substring(1) else p
    val ps = pWithoutLeadingSlash.split("/", -1).toList
    path(ps)
  }

  /**
    * Replace path with the given path segments.
    */
  def path(p1: String, p2: String, ps: String*): Uri =
    path(p1 :: p2 :: ps.toList)

  /**
    * Replace path with the given path segments.
    */
  def path(ps: scala.collection.Seq[String]): Uri =
    pathSegments(ps.toList.map(PathSegment(_)))

  /**
    * Replace path with the given path segment.
    */
  def pathSegment(s: Segment): Uri = pathSegments(List(s))

  /**
    * Replace path with the given path segment.
    */
  def pathSegments(s1: Segment, s2: Segment, ss: Segment*): Uri = pathSegments(s1 :: s2 :: ss.toList)

  /**
    * Replace path with the given path segments.
    */
  def pathSegments(ss: scala.collection.Seq[Segment]): Uri = this.copy(pathSegments = ss.toList)

  def path: Seq[String] = pathSegments.map(_.v)

  //

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
  def params(mqp: MultiQueryParams): Uri = {
    this.copy(querySegments = querySegments ++ QuerySegment.fromMultiQueryParams(mqp))
  }

  /**
    * Adds the given parameters to the query.
    */
  def params(ps: (String, String)*): Uri = {
    this.copy(querySegments = querySegments ++ ps.map {
      case (k, v) => KeyValue(k, v)
    })
  }

  def paramsMap: Map[String, String] = paramsSeq.toMap

  def multiParams: MultiQueryParams = MultiQueryParams.fromSeq(paramsSeq)

  def paramsSeq: Seq[(String, String)] = querySegments.collect {
    case KeyValue(k, v, _, _) => k -> v
  }

  def querySegment(qf: QuerySegment): Uri =
    this.copy(querySegments = querySegments :+ qf)

  //

  /**
    * Replace the fragment.
    */
  def fragment(f: String): Uri = fragment(Some(f))

  /**
    * Replace the fragment.
    */
  def fragment(f: Option[String]): Uri = fragmentSegment(f.map(FragmentSegment(_)))

  /**
    * Replace the fragment.
    */
  def fragmentSegment(s: Option[Segment]): Uri = this.copy(fragmentSegment = s)

  def fragment: Option[String] = fragmentSegment.map(_.v)

  //

  def toJavaUri: URI = new URI(toString())

  override def toString: String = {
    def encodeUserInfo(ui: UserInfo): String =
      encode(Rfc3986.UserInfo)(ui.username) + ui.password.fold("")(":" + encode(Rfc3986.UserInfo)(_))

    @tailrec
    def encodeQuerySegments(qss: List[QuerySegment], previousWasPlain: Boolean, sb: StringBuilder): String =
      qss match {
        case Nil => sb.toString()

        case Plain(v, enc) :: t =>
          encodeQuerySegments(t, previousWasPlain = true, sb.append(enc(v)))

        case Value(v, enc) :: t =>
          if (!previousWasPlain) sb.append("&")
          sb.append(enc(v))
          encodeQuerySegments(t, previousWasPlain = false, sb)

        case KeyValue(k, v, kEnc, vEnc) :: t =>
          if (!previousWasPlain) sb.append("&")
          sb.append(kEnc(k)).append("=").append(vEnc(v))
          encodeQuerySegments(t, previousWasPlain = false, sb)
      }

    val schemeS = encode(Rfc3986.Scheme)(scheme)
    val userInfoS = userInfo.fold("")(encodeUserInfo(_) + "@")
    val hostS = hostSegment.encoded
    val portS = port.fold("")(":" + _)
    val pathPrefixS = if (pathSegments.isEmpty) "" else "/"
    val pathS = pathSegments.map(_.encoded).mkString("/")
    val queryPrefixS = if (querySegments.isEmpty) "" else "?"

    val queryS = encodeQuerySegments(querySegments.toList, previousWasPlain = true, new StringBuilder())

    // https://stackoverflow.com/questions/2053132/is-a-colon-safe-for-friendly-url-use/2053640#2053640
    val fragS = fragmentSegment.fold("")(s => "#" + s.encoded)

    s"$schemeS://$userInfoS$hostS$portS$pathPrefixS$pathS$queryPrefixS$queryS$fragS"
  }
}

/**
  * `safeApply` methods return a validation error if the scheme contains illegal characers or if the host is empty.
  * `unsafeApply` throws an [[IllegalArgumentException]] if there's a validation error
  * `notValidated` doesn't perform any validation.
  */
object Uri extends UriInterpolator {
  private val AllowedSchemeCharacters = "[a-zA-Z][a-zA-Z0-9+-.]*".r
  private def validateHost(h: String): Option[String] = if (h.isEmpty) Some("Host cannot be empty") else None
  private def validateScheme(s: String) =
    if (AllowedSchemeCharacters.unapplySeq(s).isEmpty)
      Some("Scheme can only contain alphanumeric characters, +, - and .")
    else None

  //

  def safeApply(host: String): Either[String, Uri] =
    safeApply("http", None, HostSegment(host), None, Vector.empty, Vector.empty, None)
  def safeApply(host: String, port: Int): Either[String, Uri] =
    safeApply("http", None, HostSegment(host), Some(port), Vector.empty, Vector.empty, None)
  def safeApply(host: String, port: Int, path: Seq[String]): Either[String, Uri] =
    safeApply("http", None, HostSegment(host), Some(port), path.map(PathSegment(_)), Vector.empty, None)
  def safeApply(scheme: String, host: String): Either[String, Uri] =
    safeApply(scheme, None, HostSegment(host), None, Vector.empty, Vector.empty, None)
  def safeApply(scheme: String, host: String, port: Int): Either[String, Uri] =
    safeApply(scheme, None, HostSegment(host), Some(port), Vector.empty, Vector.empty, None)
  def safeApply(scheme: String, host: String, port: Int, path: Seq[String]): Either[String, Uri] =
    safeApply(scheme, None, HostSegment(host), Some(port), path.map(PathSegment(_)), Vector.empty, None)
  def safeApply(scheme: String, host: String, path: Seq[String]): Either[String, Uri] =
    safeApply(scheme, None, HostSegment(host), None, path.map(PathSegment(_)), Vector.empty, None)
  def safeApply(scheme: String, host: String, path: Seq[String], fragment: Option[String]): Either[String, Uri] =
    safeApply(
      scheme,
      None,
      HostSegment(host),
      None,
      path.map(PathSegment(_)),
      Vector.empty,
      fragment.map(FragmentSegment(_))
    )
  def safeApply(
      scheme: String,
      userInfo: Option[UserInfo],
      host: String,
      port: Option[Int],
      path: Seq[String],
      querySegments: Seq[QuerySegment],
      fragment: Option[String]
  ): Either[String, Uri] =
    safeApply(
      scheme,
      userInfo,
      HostSegment(host),
      port,
      path.map(PathSegment(_)),
      querySegments,
      fragment.map(FragmentSegment(_))
    )
  def safeApply(
      scheme: String,
      userInfo: Option[UserInfo],
      hostSegment: Segment,
      port: Option[Int],
      pathSegments: Seq[Segment],
      querySegments: Seq[QuerySegment],
      fragmentSegment: Option[Segment]
  ): Either[String, Uri] =
    Validate.all(validateScheme(scheme), validateHost(hostSegment.v))(
      notValidated(scheme, userInfo, hostSegment, port, pathSegments, querySegments, fragmentSegment)
    )

  //

  def unsafeApply(host: String): Uri =
    unsafeApply("http", None, HostSegment(host), None, Vector.empty, Vector.empty, None)
  def unsafeApply(host: String, port: Int): Uri =
    unsafeApply("http", None, HostSegment(host), Some(port), Vector.empty, Vector.empty, None)
  def unsafeApply(host: String, port: Int, path: Seq[String]): Uri =
    unsafeApply("http", None, HostSegment(host), Some(port), path.map(PathSegment(_)), Vector.empty, None)
  def unsafeApply(scheme: String, host: String): Uri =
    unsafeApply(scheme, None, HostSegment(host), None, Vector.empty, Vector.empty, None)
  def unsafeApply(scheme: String, host: String, port: Int): Uri =
    unsafeApply(scheme, None, HostSegment(host), Some(port), Vector.empty, Vector.empty, None)
  def unsafeApply(scheme: String, host: String, port: Int, path: Seq[String]): Uri =
    unsafeApply(scheme, None, HostSegment(host), Some(port), path.map(PathSegment(_)), Vector.empty, None)
  def unsafeApply(scheme: String, host: String, path: Seq[String]): Uri =
    unsafeApply(scheme, None, HostSegment(host), None, path.map(PathSegment(_)), Vector.empty, None)
  def unsafeApply(scheme: String, host: String, path: Seq[String], fragment: Option[String]): Uri =
    unsafeApply(
      scheme,
      None,
      HostSegment(host),
      None,
      path.map(PathSegment(_)),
      Vector.empty,
      fragment.map(FragmentSegment(_))
    )
  def unsafeApply(
      scheme: String,
      userInfo: Option[UserInfo],
      host: String,
      port: Option[Int],
      path: Seq[String],
      querySegments: Seq[QuerySegment],
      fragment: Option[String]
  ): Uri =
    unsafeApply(
      scheme,
      userInfo,
      HostSegment(host),
      port,
      path.map(PathSegment(_)),
      querySegments,
      fragment.map(FragmentSegment(_))
    )
  def unsafeApply(
      scheme: String,
      userInfo: Option[UserInfo],
      hostSegment: Segment,
      port: Option[Int],
      pathSegments: Seq[Segment],
      querySegments: Seq[QuerySegment],
      fragmentSegment: Option[Segment]
  ): Uri =
    safeApply(scheme, userInfo, hostSegment, port, pathSegments, querySegments, fragmentSegment).getOrThrow

  //

  def notValidated(host: String): Uri =
    notValidated("http", None, HostSegment(host), None, Vector.empty, Vector.empty, None)
  def notValidated(host: String, port: Int): Uri =
    notValidated("http", None, HostSegment(host), Some(port), Vector.empty, Vector.empty, None)
  def notValidated(host: String, port: Int, path: Seq[String]): Uri =
    notValidated("http", None, HostSegment(host), Some(port), path.map(PathSegment(_)), Vector.empty, None)
  def notValidated(scheme: String, host: String): Uri =
    notValidated(scheme, None, HostSegment(host), None, Vector.empty, Vector.empty, None)
  def notValidated(scheme: String, host: String, port: Int): Uri =
    notValidated(scheme, None, HostSegment(host), Some(port), Vector.empty, Vector.empty, None)
  def notValidated(scheme: String, host: String, port: Int, path: Seq[String]): Uri =
    notValidated(scheme, None, HostSegment(host), Some(port), path.map(PathSegment(_)), Vector.empty, None)
  def notValidated(scheme: String, host: String, path: Seq[String]): Uri =
    notValidated(scheme, None, HostSegment(host), None, path.map(PathSegment(_)), Vector.empty, None)
  def notValidated(scheme: String, host: String, path: Seq[String], fragment: Option[String]): Uri =
    notValidated(
      scheme,
      None,
      HostSegment(host),
      None,
      path.map(PathSegment(_)),
      Vector.empty,
      fragment.map(FragmentSegment(_))
    )
  def notValidated(
      scheme: String,
      userInfo: Option[UserInfo],
      host: String,
      port: Option[Int],
      path: Seq[String],
      querySegments: Seq[QuerySegment],
      fragment: Option[String]
  ): Uri =
    notValidated(
      scheme,
      userInfo,
      HostSegment(host),
      port,
      path.map(PathSegment(_)),
      querySegments,
      fragment.map(FragmentSegment(_))
    )
  def notValidated(
      scheme: String,
      userInfo: Option[UserInfo],
      hostSegment: Segment,
      port: Option[Int],
      pathSegments: Seq[Segment],
      querySegments: Seq[QuerySegment],
      fragmentSegment: Option[Segment]
  ) = new Uri(scheme, userInfo, hostSegment, port, pathSegments, querySegments, fragmentSegment)

  //

  def apply(javaUri: URI): Uri = uri"${javaUri.toString}"

  def parse(uri: String): Either[String, Uri] =
    Try(uri"$uri") match {
      case Success(u)            => Right(u)
      case Failure(e: Exception) => Left(e.getMessage)
      case Failure(t: Throwable) => throw t
    }

  case class Segment(v: String, encoding: Encoding) {
    def encoded: String = encoding(v)
  }

  object HostSegment {
    def apply(v: String): Segment = Segment(v, HostEncoding.Standard)
  }

  object PathSegment {
    def apply(v: String): Segment = Segment(v, PathSegmentEncoding.Standard)
  }

  object FragmentSegment {
    def apply(v: String): Segment = Segment(v, FragmentEncoding.Standard)
  }

  sealed trait QuerySegment
  object QuerySegment {

    /**
      * @param keyEncoding See [[Plain.encoding]]
      * @param valueEncoding See [[Plain.encoding]]
      */
    case class KeyValue(
        k: String,
        v: String,
        keyEncoding: Encoding = QuerySegmentEncoding.Standard,
        valueEncoding: Encoding = QuerySegmentEncoding.Standard
    ) extends QuerySegment

    /**
      * A query fragment which contains only the value, without a key.
      */
    case class Value(v: String, relaxedEncoding: Encoding = QuerySegmentEncoding.Standard) extends QuerySegment

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
    case class Plain(v: String, encoding: Encoding = QuerySegmentEncoding.Standard) extends QuerySegment

    private[model] def fromMultiQueryParams(mqp: MultiQueryParams): Iterable[QuerySegment] = {
      mqp.toMultiSeq.flatMap {
        case (k, vs) =>
          vs match {
            case Seq() => List(Value(k))
            case s     => s.map(v => KeyValue(k, v))
          }
      }
    }
  }

  type Encoding = String => String

  object HostEncoding {
    // TODO
    private val IpV6Pattern = "[0-9a-fA-F:]+".r

    val Standard: Encoding = {
      case s @ IpV6Pattern() if s.count(_ == ':') >= 2 => s"[$s]"
      case s                                           => UriCompatibility.encodeDNSHost(s)
    }
  }

  object PathSegmentEncoding {
    val Standard: Encoding = encode(Rfc3986.PathSegment)
  }

  object QuerySegmentEncoding {

    /**
      * Encodes all reserved characters using [[java.net.URLEncoder.encode()]].
      */
    val All: Encoding = UriCompatibility.encodeQuery(_, "UTF-8")

    /**
      * Encodes only the `&` and `=` reserved characters, which are usually
      * used to separate query parameter names and values.
      */
    val Standard: Encoding = encode(Rfc3986.QueryNoStandardDelims, spaceAsPlus = true, encodePlus = true)

    /**
      * Doesn't encode any of the reserved characters, leaving intact all
      * characters allowed in the query string as defined by RFC3986.
      */
    val Relaxed: Encoding = encode(Rfc3986.Query, spaceAsPlus = true)

    /**
      * Doesn't encode any of the reserved characters, leaving intact all
      * characters allowed in the query string as defined by RFC3986 as well
      * as the characters `[` and `]`. These brackets aren't legal in the
      * query part of the URI, but some servers use them unencoded. See
      * https://stackoverflow.com/questions/11490326/is-array-syntax-using-square-brackets-in-url-query-strings-valid
      * for discussion.
      */
    val RelaxedWithBrackets: Encoding = encode(Rfc3986.QueryWithBrackets, spaceAsPlus = true)
  }

  object FragmentEncoding {
    val Standard: Encoding = encode(Rfc3986.Fragment)
  }

  case class UserInfo(username: String, password: Option[String])
}
