package com.softwaremill.sttp

import scala.collection.mutable

object ToCurlConverter {

  def apply(request: Request[_, _]): String = {
    val params = List(extractOptions(_), extractMethod(_), extractHeaders(_), extractBody(_))
      .map(addSpaceIfNotEmpty)
      .reduce((acc, item) => r => acc(r) + item(r))
      .apply(request)

    s"curl$params ${request.uri}"
  }

  private def extractMethod(r: Request[_, _]): String = {
    s"-X ${r.method.m}"
  }

  private def extractHeaders(r: Request[_, _]): String = {
    r.headers
      .collect {
        case (k, v) => s"""-H "$k: $v""""
      }
      .mkString(" ")
  }

  private def extractBody(r: Request[_, _]): String = {
    r.body match {
      case StringBody(text, _, _) if r.headers.toMap.get(HeaderNames.ContentType).forall(_ == MediaTypes.Form) =>
        s"""-F '$text'"""
      case StringBody(text, _, _) => s"""--data '$text'"""
      case ByteArrayBody(_, _)    => ??? // TODO: what to do here?
      case _                      => ""
    }
  }

  private def extractOptions(r: Request[_, _]): String = {
    val sb = mutable.ListBuffer[String]()
    if (r.options.followRedirects) {
      sb.append("-L")
    }
    sb.append(s"--max-redirs=${r.options.maxRedirects}")
    sb.mkString(" ")
  }

  private def addSpaceIfNotEmpty(fInput: Request[_, _] => String): Request[_, _] => String =
    t => if (fInput(t).isEmpty) "" else s" ${fInput(t)}"
}
