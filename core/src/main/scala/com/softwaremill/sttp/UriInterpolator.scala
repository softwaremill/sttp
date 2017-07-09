package com.softwaremill.sttp

import java.net.{URI, URLEncoder}

object UriInterpolator {

  def interpolate(sc: StringContext, args: Any*): URI = {
    val strings = sc.parts.iterator
    val expressions = args.iterator
    var ub = UriBuilderStart.parseS(strings.next(), identity)

    while (strings.hasNext) {
      ub = ub.parseE(expressions.next())
      ub = ub.parseS(strings.next(), identity)
    }

    new URI(ub.build)
  }

  sealed trait UriBuilder {

    /**
      * @param doEncode Only values from expressions should be URI-encoded. Strings should be preserved as-is.
      */
    def parseS(s: String, doEncode: String => String): UriBuilder
    def parseE(e: Any): UriBuilder = e match {
      case s: String => parseS(s, encode(_))
      case None => this
      case null => this
      case Some(x) => parseE(x)
      case x => parseS(x.toString, encode(_))
    }
    def build: String
  }

  val UriBuilderStart = Scheme("")

  case class Scheme(v: String) extends UriBuilder {

    override def parseS(s: String, doEncode: String => String): UriBuilder = {
      val splitAtSchemeEnd = s.split("://", 2)
      splitAtSchemeEnd match {
        case Array(schemeFragment, rest) =>
          Authority(append(schemeFragment, doEncode))
            .parseS(rest, doEncode)

        case Array(x) =>
          if (!x.matches("[a-zA-Z0-9+\\.\\-]*")) {
            // anything else than the allowed characters in scheme suggest that there is no scheme
            // assuming whatever we parsed so far is part of authority, and parsing the rest
            // see https://stackoverflow.com/questions/3641722/valid-characters-for-uri-schemes
            Authority(Scheme(""), v).parseS(x, doEncode)
          } else append(x, doEncode)
      }
    }

    private def append(x: String, doEncode: String => String): Scheme =
      Scheme(v + doEncode(x))

    override def build: String = if (v.isEmpty) "" else v + "://"
  }

  case class Authority(s: Scheme, v: String = "") extends UriBuilder {

    override def parseS(s: String, doEncode: (String) => String): UriBuilder = {
      // authority is terminated by /, ?, # or end of string (there might be other /, ?, # later on e.g. in the query)
      // see https://tools.ietf.org/html/rfc3986#section-3.2
      s.split("[/\\?#]", 2) match {
        case Array(authorityFragment, rest) =>
          val splitOn = charAfterPrefix(authorityFragment, s)
          append(authorityFragment, doEncode).next(splitOn, rest, doEncode)
        case Array(x) => append(x, doEncode)
      }
    }

    private def next(splitOn: Char,
                     rest: String,
                     doEncode: (String) => String): UriBuilder =
      splitOn match {
        case '/' => Path(this).parseS(rest, doEncode)
        case '?' => Query(Path(this)).parseS(rest, doEncode)
        case '#' => Fragment(Query(Path(this))).parseS(rest, doEncode)
      }

    override def parseE(e: Any): UriBuilder = e match {
      case s: Seq[_] =>
        val newAuthority = s.map(_.toString).map(encode(_)).mkString(".")
        copy(v = v + newAuthority)
      case x => super.parseE(x)
    }

    override def build: String = {
      // remove dangling "." which might occur due to optional authority fragments
      val v2 = if (v.startsWith(".")) v.substring(1) else v
      val v3 = if (v.endsWith(".")) v2.substring(0, v2.length - 1) else v2

      s.build + v3
    }

    private def append(x: String, doEncode: String => String): Authority =
      copy(v = v + doEncode(x))
  }

  case class Path(a: Authority, vs: Vector[String] = Vector.empty)
      extends UriBuilder {

    override def parseS(s: String, doEncode: (String) => String): UriBuilder = {
      // path is terminated by ?, # or end of string (there might be other ?, # later on e.g. in the query)
      // see https://tools.ietf.org/html/rfc3986#section-3.3
      s.split("[\\?#]", 2) match {
        case Array(pathFragments, rest) =>
          val splitOn = charAfterPrefix(pathFragments, s)
          append(pathFragments, doEncode).next(splitOn, rest, doEncode)
        case Array(x) => append(x, doEncode)
      }
    }

    private def next(splitOn: Char,
                     rest: String,
                     doEncode: (String) => String): UriBuilder =
      splitOn match {
        case '?' => Query(this).parseS(rest, doEncode)
        case '#' => Fragment(Query(this)).parseS(rest, doEncode)
      }

    override def parseE(e: Any): UriBuilder = e match {
      case s: Seq[_] =>
        val newFragments = s.map(_.toString).map(encode(_))
        copy(vs = vs ++ newFragments)

      case x => super.parseE(x)
    }

    override def build: String = {
      val v = if (vs.isEmpty) "" else "/" + vs.mkString("/")
      a.build + v
    }

    private def append(fragments: String, doEncode: String => String): Path = {
      copy(vs = vs ++ fragments.split("/").map(doEncode))
    }
  }

  type QueryFragment = (Option[String], Option[String])

  case class Query(p: Path, fs: Vector[QueryFragment] = Vector.empty)
      extends UriBuilder {

    override def parseS(s: String, doEncode: (String) => String): UriBuilder = {
      s.split("#", 2) match {
        case Array(queryFragment, rest) =>
          Fragment(appendS(queryFragment)).parseS(rest, doEncode)

        case Array(x) => appendS(x)
      }
    }

    override def parseE(e: Any): UriBuilder = e match {
      case m: Map[_, _] =>
        val newFragments = m.map {
          case (k, v) =>
            (Some(encode(k, query = true)), Some(encode(v, query = true)))
        }
        copy(fs = fs ++ newFragments)

      case s: Seq[_] =>
        val newFragments = s.map {
          case (k, v) =>
            (Some(encode(k, query = true)), Some(encode(v, query = true)))
          case x => (Some(encode(x, query = true)), None)
        }
        copy(fs = fs ++ newFragments)

      case s: String => appendE(Some(encode(s, query = true)))
      case None => appendE(None)
      case null => appendE(None)
      case Some(x) => parseE(x)
      case x => appendE(Some(encode(x.toString, query = true)))
    }

    override def build: String = {
      val fragments = fs.flatMap {
        case (None, None) => None
        case (Some(k), None) => Some(k)
        case (k, v) => Some(k.getOrElse("") + "=" + v.getOrElse(""))
      }

      val query = if (fragments.isEmpty) "" else "?" + fragments.mkString("&")

      p.build + query
    }

    private def appendS(queryFragment: String): Query = {

      val newVs = queryFragment.split("&").map { nv =>
        /*
         - empty -> (None, None)
         - k=v   -> (Some(k), Some(v))
         - k=    -> (Some(k), Some(""))
         - k     -> (Some(k), None)
         - =     -> (None, Some(""))
         - =v    -> (None, Some(v))
         */
        if (nv.isEmpty) (None, None)
        else if (nv.startsWith("=")) (None, Some(nv.substring(1)))
        else
          nv.split("=", 2) match {
            case Array(n, v) => (Some(n), Some(v))
            case Array(n) => (Some(n), None)
          }
      }

      // it's possible that the current-last and new-first query fragments
      // are indeed two parts of a single fragment. Attempting to merge,
      // if possible
      val (currentInit, currentLastV) = fs.splitAt(fs.length - 1)
      val (newHeadV, newTail) = newVs.splitAt(1)

      val mergedOpt = for {
        currentLast <- currentLastV.headOption
        newHead <- newHeadV.headOption
      } yield {
        currentInit ++ merge(currentLast, newHead) ++ newTail
      }

      val combinedVs = mergedOpt match {
        case None => fs ++ newVs // either current or new fragments are empty
        case Some(merged) => merged
      }

      copy(fs = combinedVs)
    }

    private def merge(last: QueryFragment,
                      first: QueryFragment): Vector[QueryFragment] = {
      /*
       Only some combinations of fragments are possible. Part of them is
       already handled in `appendE` (specifically, any expressions of
       the form k=$v). Here we have to handle: $k=$v and $k=v.
       */
      (last, first) match {
        case ((Some(k), None), (None, Some(""))) =>
          Vector((Some(k), Some(""))) // k + =  => k=
        case ((Some(k), None), (None, Some(v))) =>
          Vector((Some(k), Some(v))) // k + =v => k=v
        case (x, y) => Vector(x, y)
      }
    }

    private def appendE(vo: Option[String]): Query = fs.lastOption match {
      case Some((Some(k), Some(""))) =>
        // k= + Some(v) -> k=v; k= + None -> remove parameter
        vo match {
          case None => copy(fs = fs.init)
          case Some(v) => copy(fs = fs.init :+ (Some(k), Some(v)))
        }
      case _ => copy(fs = fs :+ (vo, None))
    }
  }

  case class Fragment(q: Query, v: String = "") extends UriBuilder {
    override def parseS(s: String, doEncode: (String) => String): UriBuilder = {
      copy(v = v + doEncode(s))
    }

    override def build: String = q.build + (if (v.isEmpty) "" else s"#$v")
  }

  private def encode(s: Any, query: Boolean = false): String = {
    val encoded = URLEncoder.encode(String.valueOf(s), "UTF-8")
    // space is encoded as a +, which is only valid in the query;
    // in other contexts, it must be percent-encoded
    // see https://stackoverflow.com/questions/2678551/when-to-encode-space-to-plus-or-20
    if (!query) encoded.replaceAll("\\+", "%20") else encoded
  }

  private def charAfterPrefix(prefix: String, whole: String): Char = {
    val pl = prefix.length
    whole.substring(pl, pl + 1).charAt(0)
  }
}
