package com.softwaremill.sttp

import java.net.HttpCookie

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

trait ResponseExtensions[T] { self: Response[T] =>

  def cookies: Seq[Cookie] =
    headers(SetCookieHeader)
      .flatMap(h => HttpCookie.parse(h).asScala.map(hc => Cookie.apply(hc, h)))
}
