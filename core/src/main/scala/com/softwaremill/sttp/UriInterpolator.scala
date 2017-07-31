package com.softwaremill.sttp

import scala.annotation.tailrec

object UriInterpolator {

  def interpolate(sc: StringContext, args: Any*): Uri = {
    val strings = sc.parts.iterator
    val expressions = args.iterator
    var ub = UriBuilderStart.parseS(strings.next())

    while (strings.hasNext) {
      ub = ub.parseE(expressions.next())
      ub = ub.parseS(strings.next())
    }

    ub.build
  }

  sealed trait UriBuilder {
    def parseS(s: String): UriBuilder
    def parseE(e: Any): UriBuilder
    def build: Uri

    protected def parseE_skipNone(e: Any): UriBuilder = e match {
      case s: String => parseS(s)
      case None      => this
      case null      => this
      case Some(x)   => parseE(x)
      case x         => parseS(x.toString)
    }
  }

  val UriBuilderStart = Scheme("")

  case class Scheme(v: String) extends UriBuilder {

    override def parseS(s: String): UriBuilder = {
      val splitAtSchemeEnd = s.split("://", 2)
      splitAtSchemeEnd match {
        case Array(schemeFragment, rest) =>
          Authority(append(schemeFragment))
            .parseS(rest)

        case Array(x) =>
          if (!x.matches("[a-zA-Z0-9+\\.\\-]*")) {
            // anything else than the allowed characters in scheme suggest that
            // there is no scheme assuming whatever we parsed so far is part of
            // authority, and parsing the rest; see
            // https://stackoverflow.com/questions/3641722/valid-characters-for-uri-schemes
            Authority(Scheme("http"), v).parseS(x)
          } else append(x)
      }
    }

    override def parseE(e: Any): UriBuilder = parseE_skipNone(e)

    private def append(x: String): Scheme = Scheme(v + x)

    override def build: Uri = Uri(v, None, "", None, Nil, Nil, None)
  }

  case class Authority(s: Scheme, v: String = "") extends UriBuilder {

    override def parseS(s: String): UriBuilder = {
      // authority is terminated by /, ?, # or end of string (there might be
      // other /, ?, # later on e.g. in the query)
      // see https://tools.ietf.org/html/rfc3986#section-3.2
      s.split("[/\\?#]", 2) match {
        case Array(authorityFragment, rest) =>
          val splitOn = charAfterPrefix(authorityFragment, s)
          append(authorityFragment).next(splitOn, rest)
        case Array(x) => append(x)
      }
    }

    private def next(splitOn: Char, rest: String): UriBuilder =
      splitOn match {
        case '/' =>
          // prepending the leading slash as we want it preserved in the
          // output, if present
          Path(this).parseS("/" + rest)
        case '?' => Query(Path(this)).parseS(rest)
        case '#' => Fragment(Query(Path(this))).parseS(rest)
      }

    override def parseE(e: Any): UriBuilder = e match {
      case s: Seq[_] =>
        val newAuthority = s.map(_.toString).mkString(".")
        copy(v = v + newAuthority)
      case x => parseE_skipNone(x)
    }

    override def build: Uri = {
      var vv = v
      // remove dangling "." which might occur due to optional authority
      // fragments
      while (vv.startsWith(".")) vv = vv.substring(1)

      val builtS = s.build
      vv.split(":", 2) match {
        case Array(host, port) if port.matches("\\d+") =>
          builtS.copy(host = host, port = Some(port.toInt))
        case Array(x) => builtS.copy(host = x)
      }
    }

    private def append(x: String): Authority =
      copy(v = v + x)
  }

  case class Path(a: Authority, fs: Vector[String] = Vector.empty)
      extends UriBuilder {

    override def parseS(s: String): UriBuilder = {
      // path is terminated by ?, # or end of string (there might be other
      // ?, # later on e.g. in the query)
      // see https://tools.ietf.org/html/rfc3986#section-3.3
      s.split("[\\?#]", 2) match {
        case Array(pathFragments, rest) =>
          val splitOn = charAfterPrefix(pathFragments, s)
          appendS(pathFragments).next(splitOn, rest)
        case Array(x) => appendS(x)
      }
    }

    private def next(splitOn: Char, rest: String): UriBuilder =
      splitOn match {
        case '?' => Query(this).parseS(rest)
        case '#' => Fragment(Query(this)).parseS(rest)
      }

    override def parseE(e: Any): UriBuilder = e match {
      case s: Seq[_] =>
        val newFragments = s.map(_.toString).map(Some(_))
        newFragments.foldLeft(this)(_.appendE(_))
      case s: String => appendE(Some(s))
      case None      => appendE(None)
      case null      => appendE(None)
      case Some(x)   => parseE(x)
      case x         => appendE(Some(x.toString))
    }

    override def build: Uri = a.build.copy(path = fs)

    private def appendS(fragments: String): Path = {
      if (fragments.isEmpty) this
      else if (fragments.startsWith("/"))
        // avoiding an initial empty path fragment which would cause
        // initial // the build output
        copy(fs = fs ++ fragments.substring(1).split("/", -1))
      else
        copy(fs = fs ++ fragments.split("/", -1))
    }

    private def appendE(fragment: Option[String]): Path = fs.lastOption match {
      case Some("") =>
        // if the currently last path fragment is empty, replacing with the
        // expression value: corresponds to an interpolation of [anything]/$v
        copy(fs = fs.init ++ fragment)
      case _ => copy(fs = fs ++ fragment)
    }
  }

  sealed trait QueryFragment
  object QueryFragment {
    case object Empty extends QueryFragment
    case class K_Eq_V(k: String, v: String) extends QueryFragment
    case class K_Eq(k: String) extends QueryFragment
    case class K(k: String) extends QueryFragment
    case object Eq extends QueryFragment
    case class Eq_V(v: String) extends QueryFragment
  }

  case class Query(p: Path, fs: Vector[QueryFragment] = Vector.empty)
      extends UriBuilder {

    import QueryFragment._

    override def parseS(s: String): UriBuilder = {
      s.split("#", 2) match {
        case Array(queryFragment, rest) =>
          Fragment(appendS(queryFragment)).parseS(rest)

        case Array(x) => appendS(x)
      }
    }

    override def parseE(e: Any): UriBuilder = e match {
      case m: Map[_, _] => parseSeqE(m.toSeq)
      case s: Seq[_]    => parseSeqE(s)
      case s: String    => appendE(Some(s))
      case None         => appendE(None)
      case null         => appendE(None)
      case Some(x)      => parseE(x)
      case x            => appendE(Some(x.toString))
    }

    private def parseSeqE(s: Seq[_]): UriBuilder = {
      val flattenedS = s.flatMap {
        case (_, None)    => None
        case (k, Some(v)) => Some((k, v))
        case None         => None
        case Some(k)      => Some(k)
        case x            => Some(x)
      }
      val newFragments = flattenedS.map {
        case ("", "") => Eq
        case (k, "")  => K_Eq(k.toString)
        case ("", v)  => Eq_V(v.toString)
        case (k, v)   => K_Eq_V(k.toString, v.toString)
        case x        => K(x.toString)
      }
      copy(fs = fs ++ newFragments)
    }

    override def build: Uri = {
      import com.softwaremill.sttp.Uri.{QueryFragment => QF}

      val plainSeparator = QF.Plain("&", relaxedEncoding = true)
      var fragments: Vector[QF] = fs.flatMap {
        case Empty        => None
        case K_Eq_V(k, v) => Some(QF.KeyValue(k, v))
        case K_Eq(k)      => Some(QF.KeyValue(k, ""))
        case K(k)         =>
          // if we have a key-only entry, we encode it as a plain query
          // fragment
          Some(QF.Plain(k))
        case Eq      => Some(QF.KeyValue("", ""))
        case Eq_V(v) => Some(QF.KeyValue("", v))
      }

      // when serialized, plain query fragments don't have & separators
      // prepended/appended - hence, if we have parsed them here, they
      // need to be added by hand. Adding an & separator between each pair
      // of fragments where one of them is plain. For example:
      // KV P P KV KV P KV
      // becomes:
      // KV S P S P S KV KV S P S KV
      @tailrec
      def addPlainSeparators(qfs: Vector[QF],
                             previousWasPlain: Boolean,
                             acc: Vector[QF],
                             isFirst: Boolean = false): Vector[QF] = qfs match {
        case Vector() => acc
        case (p: QF.Plain) +: tail if !isFirst =>
          addPlainSeparators(tail,
                             previousWasPlain = true,
                             acc :+ plainSeparator :+ p)
        case (p: QF.Plain) +: tail =>
          addPlainSeparators(tail, previousWasPlain = true, acc :+ p)
        case (kv: QF.KeyValue) +: tail if previousWasPlain =>
          addPlainSeparators(tail,
                             previousWasPlain = false,
                             acc :+ plainSeparator :+ kv)
        case (kv: QF.KeyValue) +: tail =>
          addPlainSeparators(tail, previousWasPlain = false, acc :+ kv)
      }

      fragments = addPlainSeparators(fragments,
                                     previousWasPlain = false,
                                     Vector(),
                                     isFirst = true)

      p.build.copy(queryFragments = fragments)
    }

    private def appendS(queryFragment: String): Query = {
      val newVs = queryFragment.split("&", -1).map { nv =>
        if (nv.isEmpty) Empty
        else if (nv == "=") Eq
        else if (nv.startsWith("=")) Eq_V(nv.substring(1))
        else
          nv.split("=", 2) match {
            case Array(n, "") => K_Eq(n)
            case Array(n, v)  => K_Eq_V(n, v)
            case Array(n)     => K(n)
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
        case None         => fs ++ newVs // either current or new fragments are empty
        case Some(merged) => merged
      }

      copy(fs = combinedVs)
    }

    private def merge(last: QueryFragment,
                      first: QueryFragment): Vector[QueryFragment] = {
      /*
       Only some combinations of fragments are possible. Part of them are
       already handled in `appendE` (specifically, any expressions of
       the form k=$v). Here we have to handle: $k=$v and $k=v.
       */
      (last, first) match {
        case (K(k), Eq) => Vector(K_Eq(k)) // k + = => k=
        case (K(k), Eq_V(v)) =>
          Vector(K_Eq_V(k, v)) // k + =v => k=v
        case (x, y) => Vector(x, y)
      }
    }

    private def appendE(vo: Option[String]): Query = {
      fs.lastOption match {
        case Some(K_Eq(k)) =>
          // k= + None -> remove parameter; k= + Some(v) -> k=v
          vo match {
            case None     => copy(fs = fs.init)
            case Some("") => this
            case Some(v)  => copy(fs = fs.init :+ K_Eq_V(k, v))
          }
        case _ =>
          copy(fs = fs :+ vo.fold[QueryFragment](Empty)(K))
      }
    }
  }

  case class Fragment(q: Query, v: String = "") extends UriBuilder {
    override def parseS(s: String): UriBuilder =
      copy(v = v + s)

    override def parseE(e: Any): UriBuilder = parseE_skipNone(e)

    override def build: Uri =
      q.build.copy(fragment = if (v.isEmpty) None else Some(v))
  }

  private def charAfterPrefix(prefix: String, whole: String): Char = {
    val pl = prefix.length
    whole.substring(pl, pl + 1).charAt(0)
  }
}
