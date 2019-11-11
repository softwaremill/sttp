package sttp.client

object WwwAuthHeaderParser {
  def parse(text: String): WwwAuthHeaderValue = {
    WwwAuthHeaderValue(
      text
        .foldLeft(KeyParser(Map.empty): Parser) { (parser, char) =>
          parser.parseNext(char)
        }
        .close()
    )
  }
}

case class WwwAuthHeaderValue private (values: Map[String, String]) {
  val qop = values.get("qop")
  val digestRealm = values.get("Digest realm")
  val nonce = values.get("nonce")
  val algorithm = values.get("algorithm")
  val stale = values.get("stale")
  val opaque = values.get("opaque")
}

private case class KeyParser private (currentKey: String, parsed: Map[String, String]) extends Parser {
  override def parseNext(input: Char): Parser = {
    if (input == '=') {
      ValueParser(currentKey, parsed)
    } else {
      this.copy(currentKey = currentKey + input)
    }
  }

  override def close(): Map[String, String] = throw new IllegalStateException(this.toString)
}

private object KeyParser {
  def apply(parsed: Map[String, String]) = new KeyParser("", parsed)
}

private case class ValueParser private (
    currentKey: String,
    currentValue: String,
    parsed: Map[String, String]
) extends Parser {
  override def parseNext(input: Char): Parser = {
    if (input == '"') {
      QuotedValueParser(currentKey, parsed)
    } else {
      UnquotedValueParser(currentKey, input.toString, parsed)
    }
  }

  override def close(): Map[String, String] = throw new IllegalStateException(this.toString)
}

private object ValueParser {
  def apply(key: String, parsed: Map[String, String]) = new ValueParser(key, "", parsed)
}

private case class QuotedValueParser private (
    currentKey: String,
    currentValue: String,
    parsed: Map[String, String]
) extends Parser {
  override def parseNext(input: Char): Parser = {
    if (input == '"') {
      UnquotedValueParser(currentKey, currentValue, parsed)
    } else {
      this.copy(currentValue = currentValue + input)
    }
  }
  override def close(): Map[String, String] = throw new IllegalStateException(this.toString)
}

private object QuotedValueParser {
  def apply(key: String, parsed: Map[String, String]) = new QuotedValueParser(key, "", parsed)
}

private case class UnquotedValueParser private (
    currentKey: String,
    currentValue: String,
    parsed: Map[String, String]
) extends Parser {
  override def parseNext(input: Char): Parser = {
    if (input == ',') {
      SeparatorParser(parsed + (currentKey -> currentValue)).parseNext(input)
    } else {
      this.copy(currentValue = currentValue + input)
    }
  }
  override def close(): Map[String, String] = parsed + (currentKey -> currentValue)
}

private case class SeparatorParser(parsed: Map[String, String]) extends Parser {
  override def parseNext(input: Char): Parser = {
    input match {
      case ',' => this
      case ' ' => KeyParser(parsed)
    }
  }

  override def close(): Map[String, String] = parsed
}

private trait Parser {
  def parseNext(input: Char): Parser

  def close(): Map[String, String]
}
