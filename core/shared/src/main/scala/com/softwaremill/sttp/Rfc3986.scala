package com.softwaremill.sttp

object Rfc3986 {

  val AlphaNum: Set[Char] = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet
  val Unreserved: Set[Char] = AlphaNum ++ Set('-', '.', '_', '~')
  val SubDelims: Set[Char] = Set('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=')
  val PChar: Set[Char] = Unreserved ++ SubDelims ++ Set(':', '@')

  val Scheme: Set[Char] = AlphaNum ++ Set('+', '-', '.')
  val UserInfo: Set[Char] = Unreserved ++ SubDelims
  val Host: Set[Char] = Unreserved ++ SubDelims
  val PathSegment: Set[Char] = PChar
  val Query: Set[Char] = PChar ++ Set('/', '?')
  val Fragment: Set[Char] = Query

  val QueryNoStandardDelims: Set[Char] = Query -- Set('&', '=')
  val QueryWithBrackets: Set[Char] = Query ++ Set('[', ']')

  /**
    * @param spaceAsPlus In the query, space is encoded as a `+`. In other
    * contexts, it should be %-encoded as `%20`.
    * @param encodePlus Should `+` (which is the encoded form of space
    * in the query) be %-encoded.
    */
  def encode(allowedCharacters: Set[Char], spaceAsPlus: Boolean = false, encodePlus: Boolean = false)(
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
          sb.append(Rfc3986Compatibility.formatByte(b))
        }
      }
    }
    sb.toString
  }

}


