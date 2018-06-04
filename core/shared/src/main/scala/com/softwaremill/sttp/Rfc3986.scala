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

  def decode(plusAsSpace: Boolean = false)(s: String): String = {
    // Copied from URLDecoder.decode with additional + handling (first case)

    var needToChange = false
    val numChars = s.length
    val sb = new StringBuffer(if (numChars > 500) numChars / 2 else numChars)
    var i = 0

    var c: Char = 0
    var bytes: Array[Byte] = null
    while (i < numChars) {
      c = s.charAt(i)
      c match {
        case '+' if plusAsSpace =>
          sb.append(' ')
          i += 1
          needToChange = true
        case '%' =>
          /*
           * Starting with this instance of %, process all
           * consecutive substrings of the form %xy. Each
           * substring %xy will yield a byte. Convert all
           * consecutive  bytes obtained this way to whatever
           * character(s) they represent in the provided
           * encoding.
           */
          try {
            // (numChars-i)/3 is an upper bound for the number
            // of remaining bytes
            if (bytes == null) bytes = new Array[Byte]((numChars - i) / 3)
            var pos = 0
            while (((i + 2) < numChars) && (c == '%')) {
              val v = Integer.parseInt(s.substring(i + 1, i + 3), 16)
              if (v < 0)
                throw new IllegalArgumentException(
                  "URLDecoder: Illegal hex characters in escape (%) pattern - negative value")
              bytes(pos) = v.toByte
              pos += 1
              i += 3
              if (i < numChars) c = s.charAt(i)
            }
            // A trailing, incomplete byte encoding such as
            // "%x" will cause an exception to be thrown
            if ((i < numChars) && (c == '%'))
              throw new IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern")
            sb.append(new String(bytes, 0, pos, Utf8))
          } catch {
            case e: NumberFormatException =>
              throw new IllegalArgumentException(
                "URLDecoder: Illegal hex characters in escape (%) pattern - " + e.getMessage)
          }
          needToChange = true
        case _ =>
          sb.append(c)
          i += 1
      }
    }

    if (needToChange) sb.toString else s
  }
}
