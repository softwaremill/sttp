package com.softwaremill.sttp

import scala.annotation.tailrec

object UriInterpolator {

  def interpolate(sc: StringContext, args: Any*): Uri = {
    val tokens = tokenize(sc, args: _*)

    val builders = List(
      UriBuilder.Scheme,
      UriBuilder.UserInfo,
      UriBuilder.HostPort,
      UriBuilder.Path,
      UriBuilder.Query,
      UriBuilder.Fragment
    )

    val startingUri = Uri("-")

    val (uri, leftTokens) =
      builders.foldLeft((startingUri, tokens)) {
        case ((u, t), builder) =>
          builder.fromTokens(u, t)
      }

    if (leftTokens.nonEmpty) {
      throw new IllegalStateException(s"Tokens left after building the whole uri: $leftTokens, result so far: $uri")
    }

    uri
  }

  private def tokenize(sc: StringContext, args: Any*): Vector[Token] = {
    val strings = sc.parts.iterator
    val expressions = args.iterator

    var (tokenizer, tokens) = Tokenizer.Scheme.tokenize(strings.next())

    while (strings.hasNext) {
      val nextExpression = expressions.next()
      val nextExpressionStr = nextExpression.toString

      // special case: the interpolation starts with an expression, which
      // contains a whole URI. In this case, parsing the expression as if
      // its string value was embedded in the interpolated string. This
      // way it's possible to extend existing URIs. Without special-casing
      // the embedded URI would be escaped and become part of the host
      // as a whole.
      if (tokens == Vector(StringToken("")) && nextExpressionStr.contains("://")) {
        def tokenizeExpressionAsString(): Unit = {
          val (nextTokenizer, nextTokens) =
            tokenizer.tokenize(nextExpression.toString)
          tokenizer = nextTokenizer
          tokens = tokens ++ nextTokens
        }

        def tokenizeStringRemoveEmptyPrefix(): Unit = {
          val (nextTokenizer, nextTokens) = tokenizer.tokenize(strings.next())
          tokenizer = nextTokenizer

          // we need to remove empty tokens around exp as well - however here
          // by hand, as the expression token is unwrapped, so removeEmptyTokensAroundExp
          // won't handle this.
          val nextTokensWithoutEmptyPrefix = nextTokens match {
            case StringToken("") +: tail => tail
            case x                       => x
          }

          def isSlash(t: Token) = t == SlashInPath || t == PathStart

          // remove trailing slash when path is added to an interpolated uri:
          // val a = uri"http://example.com/" // notice the trailing slash
          // val b = uri"$a/xy" // "http://example.com/xy"
          (tokens, nextTokensWithoutEmptyPrefix) match {
            case (ts :+ t :+ StringToken(""), SlashInPath +: nt) if isSlash(t) => tokens = ts ++ (t +: nt)
            case _                                                             => tokens = tokens ++ nextTokensWithoutEmptyPrefix
          }
        }

        tokenizeExpressionAsString()
        tokenizeStringRemoveEmptyPrefix()
      } else {
        tokens = tokens :+ ExpressionToken(nextExpression)

        val (nextTokenizer, nextTokens) = tokenizer.tokenize(strings.next())
        tokenizer = nextTokenizer
        tokens = tokens ++ nextTokens
      }

    }

    removeEmptyTokensAroundExp(tokens)
  }

  sealed trait Token
  case class StringToken(s: String) extends Token
  case class ExpressionToken(e: Any) extends Token
  case object SchemeEnd extends Token
  case object ColonInAuthority extends Token
  case object AtInAuthority extends Token
  case object DotInAuthority extends Token
  case object PathStart extends Token
  case object SlashInPath extends Token
  case object QueryStart extends Token
  case object AmpInQuery extends Token
  case object EqInQuery extends Token
  case object FragmentStart extends Token

  trait Tokenizer {
    def tokenize(s: String): (Tokenizer, Vector[Token])
  }

  object Tokenizer {
    object Scheme extends Tokenizer {
      override def tokenize(s: String): (Tokenizer, Vector[Token]) = {
        s.split("://", 2) match {
          case Array(scheme, rest) =>
            val (next, authorityTokens) = Authority.tokenize(rest)
            (next, Vector(StringToken(scheme), SchemeEnd) ++ authorityTokens)

          case Array(x) =>
            if (!x.matches("[a-zA-Z0-9+\\.\\-]*")) {
              // anything else than the allowed characters in scheme suggest that
              // there is no scheme; tokenizing using the next tokenizer in chain
              // https://stackoverflow.com/questions/3641722/valid-characters-for-uri-schemes
              Authority.tokenize(x)
            } else {
              (this, Vector(StringToken(x)))
            }
        }
      }
    }

    object Authority extends Tokenizer {
      private val IpV6InAuthorityPattern = "\\[[0-9a-fA-F:]+\\]".r

      private def ipv6parser(a: String): Option[Vector[Token]] = {
        a match {
          case IpV6InAuthorityPattern() =>
            // removing the [] which are used to surround ipv6 adresses in URLs
            Some(Vector(StringToken(a.substring(1, a.length - 1))))
          case _ => None
        }
      }

      override def tokenize(s: String): (Tokenizer, Vector[Token]) =
        tokenizeTerminatedFragment(
          s,
          this,
          Set('/', '?', '#'),
          Map(':' -> ColonInAuthority, '@' -> AtInAuthority, '.' -> DotInAuthority),
          ipv6parser
        )
    }

    object Path extends Tokenizer {
      override def tokenize(s: String): (Tokenizer, Vector[Token]) =
        tokenizeTerminatedFragment(
          s,
          this,
          Set('?', '#'),
          Map('/' -> SlashInPath)
        )
    }

    object Query extends Tokenizer {
      override def tokenize(s: String): (Tokenizer, Vector[Token]) =
        tokenizeTerminatedFragment(
          s,
          this,
          Set('#'),
          Map('&' -> AmpInQuery, '=' -> EqInQuery)
        )
    }

    object Fragment extends Tokenizer {
      override def tokenize(s: String): (Tokenizer, Vector[Token]) =
        (this, Vector(StringToken(s)))
    }

    /**
      * Tokenize the given string up to any of the given terminator characters
      * by splitting it using the given separators and translating each
      * separator to a token.
      *
      * The rest of the string, after the terminators, is tokenized using
      * a tokenizer determined by the type of the terminator.
      *
      * @param extraFragmentParser A context-specific parser which is given the
      * option to tokenize a fragment (without terminators).
      */
    private def tokenizeTerminatedFragment(
        s: String,
        current: Tokenizer,
        terminators: Set[Char],
        separatorsToTokens: Map[Char, Token],
        extraFragmentParser: String => Option[Vector[Token]] = _ => None
    ): (Tokenizer, Vector[Token]) = {

      def tokenizeFragment(f: String): Vector[Token] = {
        extraFragmentParser(f) match {
          case None =>
            splitPreserveSeparators(f, separatorsToTokens.keySet).map { t =>
              t.headOption.flatMap(separatorsToTokens.get) match {
                case Some(token) => token
                case None        => StringToken(t)
              }
            }
          case Some(tt) => tt
        }
      }

      // first checking if the fragment doesn't end; e.g. the authority is
      // terminated by /, ?, # or end of string (there might be other /, ?,
      // # later on e.g. in the query).
      // See: https://tools.ietf.org/html/rfc3986#section-3.2
      split(s, terminators) match {
        case Right((fragment, separator, rest)) =>
          tokenizeAfterSeparator(tokenizeFragment(fragment), separator, rest)

        case Left(fragment) =>
          (current, tokenizeFragment(fragment))
      }
    }

    private def tokenizeAfterSeparator(
        beforeSeparatorTokens: Vector[Token],
        separator: Char,
        s: String
    ): (Tokenizer, Vector[Token]) = {

      val (next, separatorToken) = separatorTokenizerAndToken(separator)
      val (nextNext, nextTokens) = next.tokenize(s)
      (nextNext, beforeSeparatorTokens ++ Vector(separatorToken) ++ nextTokens)
    }

    private def separatorTokenizerAndToken(separator: Char): (Tokenizer, Token) =
      separator match {
        case '/' => (Path, PathStart)
        case '?' => (Query, QueryStart)
        case '#' => (Fragment, FragmentStart)
      }

    private def splitPreserveSeparators(s: String, sep: Set[Char]): Vector[String] = {
      @tailrec
      def doSplit(s: String, acc: Vector[String]): Vector[String] = {
        split(s, sep) match {
          case Left(x) => acc :+ x
          case Right((before, separator, after)) =>
            doSplit(after, acc ++ Vector(before, separator.toString))
        }
      }

      doSplit(s, Vector.empty)
    }

    private def split(s: String, sep: Set[Char]): Either[String, (String, Char, String)] = {
      val i = s.indexWhere(sep.contains)
      if (i == -1) Left(s)
      else Right((s.substring(0, i), s.charAt(i), s.substring(i + 1)))
    }
  }

  sealed trait UriBuilder {
    def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token])
  }

  object UriBuilder {

    case object Scheme extends UriBuilder {
      override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
        split(t, Set[Token](SchemeEnd)) match {
          case Left(tt) => (u.scheme("http"), tt)
          case Right((schemeTokens, _, otherTokens)) =>
            val scheme = tokensToString(schemeTokens)
            (u.scheme(scheme), otherTokens)
        }
      }
    }

    case object UserInfo extends UriBuilder {
      override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
        split(t, Set[Token](AtInAuthority)) match {
          case Left(tt) => (u, tt)
          case Right((uiTokens, _, otherTokens)) =>
            (uiFromTokens(u, uiTokens), otherTokens)
        }
      }

      private def uiFromTokens(u: Uri, uiTokens: Vector[Token]): Uri = {
        val uiTokensWithDots = uiTokens.map {
          case DotInAuthority => StringToken(".")
          case x              => x
        }
        split(uiTokensWithDots, Set[Token](ColonInAuthority)) match {
          case Left(tt) => uiFromTokens(u, tt, Vector.empty)
          case Right((usernameTokens, _, passwordTokens)) =>
            uiFromTokens(u, usernameTokens, passwordTokens)
        }
      }

      private def uiFromTokens(u: Uri, usernameTokens: Vector[Token], passwordTokens: Vector[Token]): Uri = {

        (tokensToStringOpt(usernameTokens), tokensToStringOpt(passwordTokens)) match {
          case (Some(un), Some(p)) => u.userInfo(un, p)
          case (Some(un), None)    => u.userInfo(un)
          case (None, Some(p))     => u.userInfo("", p)
          case (None, None)        => u
        }
      }
    }

    case object HostPort extends UriBuilder {
      override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
        split(t, Set[Token](PathStart, QueryStart, FragmentStart)) match {
          case Left(tt) =>
            (hostPortFromTokens(u, tt), Vector.empty)
          case Right((hpTokens, sep, otherTokens)) =>
            (hostPortFromTokens(u, hpTokens), sep +: otherTokens)
        }
      }

      private def hostPortFromTokens(u: Uri, rawHpTokens: Vector[Token]): Uri = {
        // Special case: if the host/port part contains an expression token,
        // which has a string representation which contains a colon (:), then
        // we assume that the intention was to embed the port and host separately,
        // not to escape the colon in the host name.
        val hpTokens = rawHpTokens.flatMap {
          case e: ExpressionToken =>
            val es = tokensToString(Vector(e))
            es.split(":", 2) match {
              case Array(h, p) if p.matches("\\d+") =>
                Vector(StringToken(h), ColonInAuthority, StringToken(p))
              case _ => Vector(e)
            }
          case t => Vector(t)
        }

        if (hpTokens.count(_ == ColonInAuthority) > 1) {
          throw new IllegalArgumentException("port specified multiple times")
        }

        split(hpTokens, Set[Token](ColonInAuthority)) match {
          case Left(tt) => hostFromTokens(u, tt)
          case Right((hostTokens, _, portTokens)) =>
            portFromTokens(hostFromTokens(u, hostTokens), portTokens)
        }
      }

      private def hostFromTokens(u: Uri, tokens: Vector[Token]): Uri = {
        val hostFragments = tokensToStringSeq(tokens)
        u.host(hostFragments.mkString("."))
      }

      private def portFromTokens(u: Uri, tokens: Vector[Token]): Uri = {
        u.port(tokensToStringOpt(tokens).map(_.toInt))
      }
    }

    case object Path extends UriBuilder {
      override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) =
        fromStartingToken(u, t, PathStart, Set[Token](QueryStart, FragmentStart), pathFromTokens)

      private def pathFromTokens(u: Uri, tokens: Vector[Token]): Uri = {
        u.path(tokensToStringSeq(tokens))
      }
    }

    case object Query extends UriBuilder {

      import com.softwaremill.sttp.Uri.{QueryFragment => QF}

      override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) =
        fromStartingToken(u, t, QueryStart, Set[Token](FragmentStart), queryFromTokens)

      private def queryFromTokens(u: Uri, tokens: Vector[Token]): Uri = {
        val qfs =
          splitToGroups(tokens, AmpInQuery)
            .flatMap(queryMappingsFromTokens)

        u.copy(queryFragments = qfs)
      }

      private def queryMappingsFromTokens(tokens: Vector[Token]): Vector[QF] = {
        def expressionPairToQueryFragment(ke: Any, ve: Any): Option[QF.KeyValue] =
          for {
            k <- anyToStringOpt(ke)
            v <- anyToStringOpt(ve)
          } yield QF.KeyValue(k, v)

        def seqToQueryFragments(s: Seq[_]): Vector[QF] = {
          s.flatMap {
            case (ke, ve) => expressionPairToQueryFragment(ke, ve)
            case ve       => anyToStringOpt(ve).map(QF.Value(_))
          }.toVector
        }

        split(tokens, Set[Token](EqInQuery)) match {
          case Left(Vector(ExpressionToken(e: Map[_, _]))) =>
            seqToQueryFragments(e.toSeq)
          case Left(Vector(ExpressionToken(e: Seq[_]))) =>
            seqToQueryFragments(e)
          case Left(t) => tokensToStringOpt(t, decodePlusAsSpace = true).map(QF.Value(_)).toVector
          case Right((leftEq, _, rightEq)) =>
            tokensToStringOpt(leftEq, decodePlusAsSpace = true) match {
              case Some(k) =>
                tokensToStringSeq(rightEq, decodePlusAsSpace = true).map(QF.KeyValue(k, _)).toVector

              case None =>
                Vector.empty
            }
        }
      }
    }

    case object Fragment extends UriBuilder {
      override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
        t match {
          case FragmentStart +: tt =>
            (u.fragment(tokensToStringOpt(tt)), Vector.empty)

          case _ => (u, t)
        }
      }
    }

    /**
      * Parse a prefix of tokens `t` into a component of a URI. The component
      * is only present in the tokens if there's a `startingToken`; otherwise
      * the component is skipped.
      *
      * The component is terminated by any of `nextComponentTokens`.
      */
    private def fromStartingToken(
        u: Uri,
        t: Vector[Token],
        startingToken: Token,
        nextComponentTokens: Set[Token],
        componentFromTokens: (Uri, Vector[Token]) => Uri
    ): (Uri, Vector[Token]) = {

      t match {
        case `startingToken` +: tt =>
          split(tt, nextComponentTokens) match {
            case Left(ttt) =>
              (componentFromTokens(u, ttt), Vector.empty)
            case Right((componentTokens, sep, otherTokens)) =>
              (componentFromTokens(u, componentTokens), sep +: otherTokens)
          }

        case _ => (u, t)
      }
    }

    private def anyToString(a: Any): String = anyToStringOpt(a).getOrElse("")

    private def anyToStringOpt(a: Any): Option[String] = a match {
      case None    => None
      case null    => None
      case Some(x) => Some(x.toString)
      case x       => Some(x.toString)
    }

    /*
    #102: the + sign should be decoded into a space only when it's part of the query. Otherwise, it should be
    kept as-is.
     */
    private def tokensToStringSeq(tokens: Vector[Token], decodePlusAsSpace: Boolean = false): Seq[String] = {
      /*
      #40: when converting tokens to a string sequence, we have to look at
      groups of string/expression (value) tokens separated by others. If there
      are multiple tokens in each such group, their string representations
      should be concatenated (corresponds to e.g. $x$y). A single
      collection-valued token should be expanded.
       */

      def isValueToken(t: Token) = t match {
        case ExpressionToken(_) => true
        case StringToken(_)     => true
        case _                  => false
      }

      @tailrec
      def doToSeq(ts: Vector[Token], acc: Vector[String]): Seq[String] = {
        val tsWithValuesPrefix = ts.dropWhile(to => !isValueToken(to))
        val (valueTs, tailTs) = tsWithValuesPrefix.span(isValueToken)

        valueTs match {
          case Vector() => acc // tailTs must be empty then as well
          case Vector(ExpressionToken(s: Iterable[_])) =>
            doToSeq(tailTs, acc ++ s.flatMap(anyToStringOpt).toVector)
          case Vector(ExpressionToken(s: Array[_])) =>
            doToSeq(tailTs, acc ++ s.flatMap(anyToStringOpt).toVector)
          case _ =>
            val values = valueTs
              .flatMap {
                case ExpressionToken(e) => anyToStringOpt(e)
                case StringToken(s)     => Some(decode(s, decodePlusAsSpace))
                case _                  => None
              }

            val strToAdd =
              if (values.isEmpty) None else Some(values.mkString(""))

            doToSeq(tailTs, acc ++ strToAdd)
        }
      }

      doToSeq(tokens, Vector.empty)
    }

    private def tokensToStringOpt(t: Vector[Token], decodePlusAsSpace: Boolean = false): Option[String] = t match {
      case Vector()                   => None
      case Vector(ExpressionToken(e)) => anyToStringOpt(e)
      case _                          => Some(tokensToString(t, decodePlusAsSpace))
    }

    private def tokensToString(t: Vector[Token], decodePlusAsSpace: Boolean = false): String =
      t.collect {
          case StringToken(s)     => decode(s, decodePlusAsSpace)
          case ExpressionToken(e) => anyToString(e)
        }
        .mkString("")

    private def split[T](v: Vector[T], sep: Set[T]): Either[Vector[T], (Vector[T], T, Vector[T])] = {
      val i = v.indexWhere(sep.contains)
      if (i == -1) Left(v) else Right((v.take(i), v(i), v.drop(i + 1)))
    }

    private def splitToGroups[T](v: Vector[T], sep: T): Vector[Vector[T]] = {
      @tailrec
      def doSplit(vv: Vector[T], acc: Vector[Vector[T]]): Vector[Vector[T]] = {
        vv.indexOf(sep) match {
          case -1 => acc :+ vv
          case i  => doSplit(vv.drop(i + 1), acc :+ vv.take(i))
        }
      }

      doSplit(v, Vector.empty)
    }

    private def decode(s: String, decodePlusAsSpace: Boolean): String = Rfc3986.decode(decodePlusAsSpace)(s)
  }

  /**
    * After tokenizing, there might be extra empty string tokens
    * (`StringToken("")`) before and after expressions. For example,
    * `key=$value` will tokenize to:
    *
    * `Vector(StringToken("key"), EqInQuery, StringToken(""), ExpressionToken(value))`
    *
    * These empty string tokens need to be removed so that e.g. extra key-value
    * mappings are not generated.
    */
  private def removeEmptyTokensAroundExp(tokens: Vector[Token]): Vector[Token] = {
    @tailrec
    def doRemove(t: Vector[Token], acc: Vector[Token]): Vector[Token] =
      t match {
        case StringToken("") +: (e: ExpressionToken) +: tail =>
          doRemove(e +: tail, acc)
        case (e: ExpressionToken) +: StringToken("") +: tail =>
          doRemove(tail, acc :+ e)
        case v +: tail => doRemove(tail, acc :+ v)
        case Vector()  => acc
      }

    doRemove(tokens, Vector.empty)
  }
}
