package com.softwaremill.sttp

import java.net.URLDecoder

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

    val startingUri = Uri("")

    val (uri, leftTokens) =
      builders.foldLeft((startingUri, tokens)) {
        case ((u, t), builder) =>
          builder.fromTokens(u, t)
      }

    if (leftTokens.nonEmpty) {
      throw new IllegalStateException(
        s"Tokens left after building the whole uri: $leftTokens, result so far: $uri")
    }

    uri
  }

  private def tokenize(sc: StringContext, args: Any*): Vector[Token] = {
    val strings = sc.parts.iterator
    val expressions = args.iterator

    var (tokenizer, tokens) = Tokenizer.Scheme.tokenize(strings.next())

    while (strings.hasNext) {
      val nextExpression = expressions.next()

      // special case: the interpolation starts with an expression, which
      // contains a whole URI. In this case, parsing the expression as if
      // its string value was embedded in the interpolated string. This
      // way it's possible to extend existing URIs. Without special-casing
      // the embedded URI would be escaped and become part of the host
      // as a whole.
      if (tokens == Vector(StringToken("")) && nextExpression.toString.contains(
            "://")) {
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

          tokens = tokens ++ nextTokensWithoutEmptyPrefix
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
      override def tokenize(s: String): (Tokenizer, Vector[Token]) =
        tokenizeTerminatedFragment(
          s,
          this,
          Set('/', '?', '#'),
          Map(':' -> ColonInAuthority,
              '@' -> AtInAuthority,
              '.' -> DotInAuthority)
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
      */
    private def tokenizeTerminatedFragment(
        s: String,
        current: Tokenizer,
        terminators: Set[Char],
        separatorsToTokens: Map[Char, Token]): (Tokenizer, Vector[Token]) = {

      def tokenizeFragment(f: String): Vector[Token] = {
        splitPreserveSeparators(f, separatorsToTokens.keySet).map { t =>
          t.headOption.flatMap(separatorsToTokens.get) match {
            case Some(token) => token
            case None        => StringToken(t)
          }
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
        s: String): (Tokenizer, Vector[Token]) = {

      val (next, separatorToken) = separatorTokenizerAndToken(separator)
      val (nextNext, nextTokens) = next.tokenize(s)
      (nextNext, beforeSeparatorTokens ++ Vector(separatorToken) ++ nextTokens)
    }

    private def separatorTokenizerAndToken(
        separator: Char): (Tokenizer, Token) =
      separator match {
        case '/' => (Path, PathStart)
        case '?' => (Query, QueryStart)
        case '#' => (Fragment, FragmentStart)
      }

    private def splitPreserveSeparators(s: String,
                                        sep: Set[Char]): Vector[String] = {
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

    private def split(
        s: String,
        sep: Set[Char]): Either[String, (String, Char, String)] = {
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

      private def uiFromTokens(u: Uri,
                               usernameTokens: Vector[Token],
                               passwordTokens: Vector[Token]): Uri = {

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

      private def hostPortFromTokens(u: Uri, hpTokens: Vector[Token]): Uri = {
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
        fromStartingToken(u,
                          t,
                          PathStart,
                          Set[Token](QueryStart, FragmentStart),
                          pathFromTokens)

      private def pathFromTokens(u: Uri, tokens: Vector[Token]): Uri = {
        u.path(tokensToStringSeq(tokens))
      }
    }

    case object Query extends UriBuilder {

      import com.softwaremill.sttp.Uri.{QueryFragment => QF}

      override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) =
        fromStartingToken(u,
                          t,
                          QueryStart,
                          Set[Token](FragmentStart),
                          queryFromTokens)

      private def queryFromTokens(u: Uri, tokens: Vector[Token]): Uri = {
        val qfs =
          splitToGroups(tokens, AmpInQuery)
            .flatMap(queryMappingsFromTokens)

        u.copy(queryFragments = qfs)
      }

      private def queryMappingsFromTokens(tokens: Vector[Token]): Vector[QF] = {
        def expressionPairToQueryFragment(ke: Any,
                                          ve: Any): Option[QF.KeyValue] =
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
          case Left(t) => tokensToStringOpt(t).map(QF.Value(_)).toVector
          case Right((leftEq, _, rightEq)) =>
            tokensToStringOpt(leftEq) match {
              case Some(k) =>
                tokensToStringSeq(rightEq).map(QF.KeyValue(k, _)).toVector

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
        componentFromTokens: (Uri, Vector[Token]) => Uri)
      : (Uri, Vector[Token]) = {

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

    private def tokensToStringSeq(t: Vector[Token]): Seq[String] = t.flatMap {
      case ExpressionToken(s: Seq[_]) => s.flatMap(anyToStringOpt).toVector
      case ExpressionToken(e)         => anyToStringOpt(e).toVector
      case StringToken(s)             => Vector(decode(s))
      case _                          => Vector.empty
    }

    private def tokensToStringOpt(t: Vector[Token]): Option[String] = t match {
      case Vector()                   => None
      case Vector(ExpressionToken(e)) => anyToStringOpt(e)
      case _                          => Some(tokensToString(t))
    }

    private def tokensToString(t: Vector[Token]): String =
      t.collect {
          case StringToken(s)     => decode(s)
          case ExpressionToken(e) => anyToString(e)
        }
        .mkString("")

    private def split[T](
        v: Vector[T],
        sep: Set[T]): Either[Vector[T], (Vector[T], T, Vector[T])] = {
      val i = v.indexWhere(sep.contains)
      if (i == -1) Left(v) else Right((v.take(i), v(i), v.drop(i + 1)))
    }

    private def splitToGroups[T](v: Vector[T], sep: T): Vector[Vector[T]] = {
      def doSplit(vv: Vector[T], acc: Vector[Vector[T]]): Vector[Vector[T]] = {
        vv.indexOf(sep) match {
          case -1 => acc :+ vv
          case i  => doSplit(vv.drop(i + 1), acc :+ vv.take(i))
        }
      }

      doSplit(v, Vector.empty)
    }

    private def decode(s: String): String = URLDecoder.decode(s, Utf8)
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
