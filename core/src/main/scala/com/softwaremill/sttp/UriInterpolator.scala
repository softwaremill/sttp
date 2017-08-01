package com.softwaremill.sttp

import java.net.URLDecoder

import scala.annotation.tailrec

object UriInterpolator {

  def interpolate(sc: StringContext, args: Any*): Uri = {
    val strings = sc.parts.iterator
    val expressions = args.iterator

    var (tokenizer, tokens) = SchemeTokenizer.tokenize(strings.next())

    while (strings.hasNext) {
      // TODO
      val exp = expressions.next()
      if (tokens == Vector(StringToken("")) && exp.toString.contains("://")) {
        val (nextTokenizer, nextTokens) = tokenizer.tokenize(exp.toString)
        val (nextTokenizer2, nextTokens2) =
          nextTokenizer.tokenize(strings.next())

        val nextTokens3 = nextTokens2 match {
          case StringToken("") +: tail => tail
          case x                       => x
        }

        tokenizer = nextTokenizer2
        tokens = tokens ++ nextTokens ++ nextTokens3
      } else {
        tokens = tokens :+ ExpressionToken(exp)

        val (nextTokenizer, nextTokens) = tokenizer.tokenize(strings.next())
        tokenizer = nextTokenizer
        tokens = tokens ++ nextTokens
      }

    }

    val builders = List(
      SchemeBuilder,
      UserInfoBuilder,
      HostPortBuilder,
      PathBuilder,
      QueryBuilder,
      FragmentBuilder
    )

    val startingUri = Uri("")

    val (uri, leftTokens) =
      builders.foldLeft((startingUri, removeEmptyTokensAroundExp(tokens))) {
        case ((u, t), builder) =>
          builder.fromTokens(u, t)
      }

    if (leftTokens.nonEmpty) {
      throw new IllegalArgumentException(
        s"Tokens left after building the uri: $leftTokens")
    }

    uri
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

  object SchemeTokenizer extends Tokenizer {
    override def tokenize(s: String): (Tokenizer, Vector[Token]) = {
      s.split("://", 2) match {
        case Array(scheme, rest) =>
          val (next, authorityTokens) = AuthorityTokenizer.tokenize(rest)
          (next, Vector(StringToken(scheme), SchemeEnd) ++ authorityTokens)

        case Array(x) =>
          if (!x.matches("[a-zA-Z0-9+\\.\\-]*")) {
            // anything else than the allowed characters in scheme suggest that
            // there is no scheme; tokenizing using the next tokenizer in chain
            // https://stackoverflow.com/questions/3641722/valid-characters-for-uri-schemes
            AuthorityTokenizer.tokenize(x)
          } else {
            (this, Vector(StringToken(x)))
          }
      }
    }
  }

  object AuthorityTokenizer extends Tokenizer {
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

  object PathTokenizer extends Tokenizer {
    override def tokenize(s: String): (Tokenizer, Vector[Token]) =
      tokenizeTerminatedFragment(
        s,
        this,
        Set('?', '#'),
        Map('/' -> SlashInPath)
      )
  }

  object QueryTokenizer extends Tokenizer {
    override def tokenize(s: String): (Tokenizer, Vector[Token]) =
      tokenizeTerminatedFragment(
        s,
        this,
        Set('#'),
        Map('&' -> AmpInQuery, '=' -> EqInQuery)
      )
  }

  object FragmentTokenizer extends Tokenizer {
    override def tokenize(s: String): (Tokenizer, Vector[Token]) =
      (this, Vector(StringToken(s)))
  }

  private def tokenizeTerminatedFragment(
      s: String,
      current: Tokenizer,
      terminators: Set[Char],
      fragmentCharsToTokens: Map[Char, Token]): (Tokenizer, Vector[Token]) = {
    def tokenizeFragment(f: String): Vector[Token] = {
      splitPreserveSeparators(f, fragmentCharsToTokens.keySet).map { t =>
        t.headOption.flatMap(fragmentCharsToTokens.get) match {
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

  private def tokenizeAfterSeparator(beforeSeparatorTokens: Vector[Token],
                                     separator: Char,
                                     s: String): (Tokenizer, Vector[Token]) = {
    val (next, separatorToken) = separatorTokenizerAndToken(separator)
    val (nextNext, nextTokens) = next.tokenize(s)
    (nextNext, beforeSeparatorTokens ++ Vector(separatorToken) ++ nextTokens)
  }

  private def separatorTokenizerAndToken(separator: Char): (Tokenizer, Token) =
    separator match {
      case '/' => (PathTokenizer, PathStart)
      case '?' => (QueryTokenizer, QueryStart)
      case '#' => (FragmentTokenizer, FragmentStart)
    }

  sealed trait UriBuilder {
    def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token])
  }

  case object SchemeBuilder extends UriBuilder {
    override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
      splitV(t, Set[Token](SchemeEnd)) match {
        case Left(tt) => (u.scheme("http"), tt)
        case Right((schemeTokens, _, otherTokens)) =>
          val scheme = tokensToString(schemeTokens)
          (u.scheme(scheme), otherTokens)
      }
    }
  }

  case object UserInfoBuilder extends UriBuilder {
    override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
      splitV(t, Set[Token](AtInAuthority)) match {
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
      splitV(uiTokensWithDots, Set[Token](ColonInAuthority)) match {
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

  case object HostPortBuilder extends UriBuilder {
    override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
      splitV(t, Set[Token](PathStart, QueryStart, FragmentStart)) match {
        case Left(tt) =>
          (hostPortFromTokens(u, tt), Vector.empty)
        case Right((hpTokens, sep, otherTokens)) =>
          (hostPortFromTokens(u, hpTokens), sep +: otherTokens)
      }
    }

    private def hostPortFromTokens(u: Uri, hpTokens: Vector[Token]): Uri = {
      splitV(hpTokens, Set[Token](ColonInAuthority)) match {
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

  case object PathBuilder extends UriBuilder {
    override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
      t match {
        case PathStart +: tt =>
          splitV(tt, Set[Token](QueryStart, FragmentStart)) match {
            case Left(ttt) =>
              (pathFromTokens(u, ttt), Vector.empty)
            case Right((pathTokens, sep, otherTokens)) =>
              (pathFromTokens(u, pathTokens), sep +: otherTokens)
          }

        case _ => (u, t)
      }
    }

    private def pathFromTokens(u: Uri, tokens: Vector[Token]): Uri = {
      u.path(tokensToStringSeq(tokens))
    }
  }

  case object QueryBuilder extends UriBuilder {
    import com.softwaremill.sttp.Uri.{QueryFragment => QF}

    override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
      t match {
        case QueryStart +: tt =>
          splitV(tt, Set[Token](FragmentStart)) match {
            case Left(ttt) =>
              (queryFromTokens(u, ttt), Vector.empty)
            case Right((queryTokens, sep, otherTokens)) =>
              (queryFromTokens(u, queryTokens), sep +: otherTokens)
          }

        case _ => (u, t)
      }
    }

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

      splitV(tokens, Set[Token](EqInQuery)) match {
        case Left(Vector(ExpressionToken(e: Map[_, _]))) =>
          seqToQueryFragments(e.toSeq)
        case Left(Vector(ExpressionToken(e: Seq[_]))) => seqToQueryFragments(e)
        case Left(t)                                  => tokensToStringOpt(t).map(QF.Value(_)).toVector
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

  case object FragmentBuilder extends UriBuilder {
    override def fromTokens(u: Uri, t: Vector[Token]): (Uri, Vector[Token]) = {
      t match {
        case FragmentStart +: tt =>
          (u.fragment(tokensToStringOpt(tt)), Vector.empty)

        case _ => (u, t)
      }
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

  // TODO
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

  private def splitV[T](
      v: Vector[T],
      sep: Set[T]): Either[Vector[T], (Vector[T], T, Vector[T])] = {
    val i = v.indexWhere(sep.contains)
    if (i == -1) Left(v) else Right((v.take(i), v(i), v.drop(i + 1)))
  }

  private def split(s: String,
                    sep: Set[Char]): Either[String, (String, Char, String)] = {
    val i = s.indexWhere(sep.contains)
    if (i == -1) Left(s)
    else Right((s.substring(0, i), s.charAt(i), s.substring(i + 1)))
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

  private def decode(s: String): String = URLDecoder.decode(s, Utf8)
}
